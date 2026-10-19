package com.example.hdify.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import com.example.hdify.data.preferences.UserPreferencesRepository
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineType
import com.example.hdify.engine.ProgressCallback
import com.example.hdify.engine.UpscaleEngineFactory
import com.example.hdify.engine.UpscaleResult
import com.example.hdify.logging.BreadcrumbManager
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class UpscaleRequest(
    val sourceUri: Uri,
    val scale: Int,
    val engineType: EngineType = EngineType.REAL_CUGAN,
    val modelDir: String = "models-se",
    val denoise: Int = 0,
    val tileSize: Int = 0,
    val useFp16: Boolean = true,
    val isCropPreview: Boolean = false,
    val format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    val quality: Int = 100,
    val customTargetWidth: Int = 0,
    val customTargetHeight: Int = 0,
    val existingJobId: Long = 0L
)

sealed class ImageUpscaleStatus {
    data class Progress(val current: Int, val total: Int, val percent: Int) : ImageUpscaleStatus()
    data class Success(
        val outputUri: Uri,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val backend: String,
        val deviceName: String
    ) : ImageUpscaleStatus()
    data class Canceled(val durationMs: Long) : ImageUpscaleStatus()
    data class Error(val code: String, val message: String) : ImageUpscaleStatus()
}

@Singleton
class UpscaleImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineFactory: UpscaleEngineFactory,
    private val jobDao: JobDao,
    private val prefsRepo: UserPreferencesRepository
) {
    private var currentActiveEngineType: EngineType? = null
    @Volatile private var isCancelled = false
    private val dbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun execute(
        request: UpscaleRequest,
        onProgress: (ImageUpscaleStatus.Progress) -> Unit
    ): ImageUpscaleStatus = withContext(Dispatchers.IO) {
        isCancelled = false
        val startTime = System.currentTimeMillis()
        var jobId: Long = 0L

        try {
            val engineNameStr = if (request.engineType == EngineType.REAL_CUGAN) {
                "Real-CUGAN (${request.modelDir})"
            } else {
                "Real-ESRGAN (${request.modelDir})"
            }

            // 1. Reuse existing job record or insert new one in Room
            jobId = if (request.existingJobId > 0L) {
                request.existingJobId
            } else {
                jobDao.insertJob(
                    JobEntity(
                        mediaType = "IMAGE",
                        sourceUri = request.sourceUri.toString(),
                        outputUri = null,
                        scale = request.scale,
                        engineName = engineNameStr,
                        status = "PROCESSING",
                        progress = 0
                    )
                )
            }
            com.example.hdify.service.UpscaleForegroundService.currentActiveJobId = jobId

            BreadcrumbManager.add("Job #$jobId: Start decode source URI")
            HdLogger.i(TAG, "[job=$jobId stage=DECODE] Start decoding image")

            // 2. Decode bitmap
            val inBitmap = decodeBitmap(request.sourceUri, request.isCropPreview)
            if (inBitmap == null) {
                val errCode = "HD-IMG-301"
                val errMsg = "Gagal memuat atau mendecode file gambar"
                updateJobFailure(jobId, errCode, errMsg)
                return@withContext ImageUpscaleStatus.Error(errCode, errMsg)
            }

            // Check minimum dimension (< 32 px) as per PRD Section 10
            if (inBitmap.width < 32 || inBitmap.height < 32) {
                val errCode = "HD-IMG-304"
                val errMsg = "Dimensi gambar terlalu kecil (${inBitmap.width}x${inBitmap.height} px, minimum 32 px)"
                updateJobFailure(jobId, errCode, errMsg)
                return@withContext ImageUpscaleStatus.Error(errCode, errMsg)
            }

            val targetWidth = inBitmap.width * request.scale
            val targetHeight = inBitmap.height * request.scale

            BreadcrumbManager.add("Job #$jobId: Decoded ${inBitmap.width}x${inBitmap.height} -> Target ${targetWidth}x${targetHeight}")

            // 3. Sanity check: memory / dimension
            val estimatedPixelCount = targetWidth.toLong() * targetHeight.toLong()
            if (estimatedPixelCount > 100_000_000L) { // > 100 MP warning
                HdLogger.w(TAG, "[job=$jobId stage=PRECHECK] Target resolution very large: ${targetWidth}x${targetHeight} (${estimatedPixelCount / 1_000_000} MP)")
            }

            // 4. Allocate output bitmap for model upscale
            val outBitmap = try {
                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            } catch (oom: OutOfMemoryError) {
                val errCode = "HD-IMG-304"
                val errMsg = "Memori tidak cukup untuk membuat gambar hasil ${targetWidth}x${targetHeight}"
                HdLogger.e(TAG, "[job=$jobId stage=ALLOC] $errCode: $errMsg")
                updateJobFailure(jobId, errCode, errMsg)
                return@withContext ImageUpscaleStatus.Error(errCode, errMsg)
            }

            // 5. Initialize Engine via factory
            val engine = engineFactory.getEngine(request.engineType)
            currentActiveEngineType = request.engineType

            val allowCpuFallback = prefsRepo.allowCpuFallbackFlow.first()
            val engineConfig = EngineConfig(
                engine = request.engineType,
                modelDirectory = request.modelDir,
                scale = request.scale,
                denoise = request.denoise,
                tileSize = request.tileSize,
                useFp16 = request.useFp16,
                gpuIndex = 0,
                allowCpuFallback = allowCpuFallback
            )

            val engineInfo = try {
                engine.init(context.assets, engineConfig)
            } catch (e: Exception) {
                val errCode = if (!allowCpuFallback) "HD-HW-101" else "HD-MDL-201"
                val errMsg = e.message ?: "Inisialisasi engine gagal"
                updateJobFailure(jobId, errCode, errMsg)
                return@withContext ImageUpscaleStatus.Error(errCode, errMsg)
            }

            BreadcrumbManager.add("Job #$jobId: Engine ready backend=${engineInfo.backend} tile=${engineInfo.tileSize}")
            HdLogger.i(TAG, "[job=$jobId stage=UPSCALE tile=0/0] backend=${engineInfo.backend} device=${engineInfo.deviceName} fp16=${engineInfo.fp16Active} tile=${engineInfo.tileSize}")

            if (isCancelled) {
                HdLogger.i(TAG, "[job=$jobId stage=UPSCALE] Already canceled before engine upscale")
                val job = jobDao.getJobById(jobId)
                if (job != null) {
                    jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                }
                return@withContext ImageUpscaleStatus.Canceled(System.currentTimeMillis() - startTime)
            }

            // 6. Run upscale with progress
            var lastDbUpdateMs = 0L
            val progressCallback = ProgressCallback { cur, tot ->
                if (isCancelled) {
                    try {
                        engine.cancel()
                    } catch (_: Exception) {}
                }
                val pct = if (tot > 0) (cur * 100) / tot else 0
                onProgress(ImageUpscaleStatus.Progress(cur, tot, pct))
                BreadcrumbManager.add("Job #$jobId: Progress tile $cur/$tot ($pct%)")

                val now = System.currentTimeMillis()
                if (jobId > 0 && (now - lastDbUpdateMs > 250 || pct == 100)) {
                    lastDbUpdateMs = now
                    dbScope.launch {
                        try {
                            jobDao.getJobById(jobId)?.let { job ->
                                if (job.status == "PROCESSING") {
                                    jobDao.updateJob(job.copy(progress = pct))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val result = engine.upscale(inBitmap, outBitmap, progressCallback)
            try { inBitmap.recycle() } catch (_: Throwable) {}

            when (result) {
                is UpscaleResult.Success -> {
                    // Check if custom target resolution was requested (FR-IMG-09)
                    val finalBitmap = if (request.customTargetWidth > 0 && request.customTargetHeight > 0 &&
                        (request.customTargetWidth != targetWidth || request.customTargetHeight != targetHeight)) {
                        HdLogger.i(TAG, "[job=$jobId stage=RESIZE] Resizing to custom target ${request.customTargetWidth}x${request.customTargetHeight}")
                        val scaled = Bitmap.createScaledBitmap(outBitmap, request.customTargetWidth, request.customTargetHeight, true)
                        try { outBitmap.recycle() } catch (_: Throwable) {}
                        scaled
                    } else {
                        outBitmap
                    }

                    // 7. Save output
                    val outUri = saveOutputBitmap(finalBitmap, request)
                    val totalDuration = System.currentTimeMillis() - startTime

                    // Copy EXIF metadata if applicable
                    copyExif(request.sourceUri, outUri)

                    // Update Room record
                    val job = jobDao.getJobById(jobId)
                    if (job != null) {
                        jobDao.updateJob(
                            job.copy(
                                outputUri = outUri.toString(),
                                status = "COMPLETED",
                                progress = 100,
                                completedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    HdLogger.i(TAG, "[job=$jobId stage=UPSCALE] Finished successfully in ${totalDuration}ms output=$outUri")
                    BreadcrumbManager.add("Job #$jobId: Upscale completed")

                    ImageUpscaleStatus.Success(
                        outputUri = outUri,
                        width = finalBitmap.width,
                        height = finalBitmap.height,
                        durationMs = totalDuration,
                        backend = engineInfo.backend,
                        deviceName = engineInfo.deviceName
                    )
                }
                is UpscaleResult.Canceled -> {
                    val job = jobDao.getJobById(jobId)
                    if (job != null) {
                        jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                    }
                    HdLogger.i(TAG, "[job=$jobId stage=UPSCALE] Canceled by user")
                    ImageUpscaleStatus.Canceled(System.currentTimeMillis() - startTime)
                }
                is UpscaleResult.Error -> {
                    updateJobFailure(jobId, result.code, result.message)
                    ImageUpscaleStatus.Error(result.code, result.message)
                }
            }
        } catch (ce: CancellationException) {
            HdLogger.i(TAG, "[job=$jobId stage=UPSCALE] Coroutine cancelled by user")
            val job = jobDao.getJobById(jobId)
            if (job != null) {
                jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
            }
            ImageUpscaleStatus.Canceled(System.currentTimeMillis() - startTime)
        } catch (t: Throwable) {
            val errCode = "HD-SYS-999"
            val errMsg = t.message ?: "Unexpected error during upscale"
            HdLogger.e(TAG, "[job=$jobId stage=FATAL] $errCode $errMsg", t)
            updateJobFailure(jobId, errCode, errMsg)
            ImageUpscaleStatus.Error(errCode, errMsg)
        } finally {
            com.example.hdify.service.UpscaleForegroundService.currentActiveJobId = 0L
            currentActiveEngineType = null
        }
    }

    fun cancel() {
        isCancelled = true
        try {
            engineFactory.getEngine(EngineType.REAL_CUGAN).cancel()
            engineFactory.getEngine(EngineType.REAL_ESRGAN).cancel()
        } catch (e: Exception) {
            HdLogger.w(TAG, "Error cancelling engine: ${e.message}")
        }
    }

    private fun decodeBitmap(uri: Uri, isCropPreview: Boolean): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            var bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }

            if (bmp.config != Bitmap.Config.ARGB_8888) {
                val converted = bmp.copy(Bitmap.Config.ARGB_8888, true)
                bmp.recycle()
                bmp = converted
            }

            if (isCropPreview) {
                // Crop center 256x256
                val cropSize = 256
                val cx = Math.max(0, (bmp.width - cropSize) / 2)
                val cy = Math.max(0, (bmp.height - cropSize) / 2)
                val cw = Math.min(cropSize, bmp.width)
                val ch = Math.min(cropSize, bmp.height)
                val cropped = Bitmap.createBitmap(bmp, cx, cy, cw, ch)
                if (cropped != bmp) {
                    bmp.recycle()
                }
                return cropped
            }

            bmp
        } catch (e: Exception) {
            HdLogger.e(TAG, "HD-IMG-301 Failed to decode source image: ${e.message}", e)
            null
        }
    }

    private suspend fun saveOutputBitmap(bitmap: Bitmap, request: UpscaleRequest): Uri {
        val extension = when (request.format) {
            Bitmap.CompressFormat.JPEG -> "jpg"
            Bitmap.CompressFormat.PNG -> "png"
            else -> "webp"
        }
        val mimeType = when (request.format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> "image/webp"
        }
        val filename = "HDify_${System.currentTimeMillis()}.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/HDify")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("HD-STO-601 Failed to create MediaStore record")

            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(request.format, request.quality, stream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            val dir = File(context.getExternalFilesDir(null), "Pictures/HDify")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { stream ->
                bitmap.compress(request.format, request.quality, stream)
            }
            return Uri.fromFile(file)
        }
    }

    private fun copyExif(sourceUri: Uri, destUri: Uri) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
                val srcExif = ExifInterface(inStream)
                context.contentResolver.openFileDescriptor(destUri, "rw")?.use { pfd ->
                    val dstExif = ExifInterface(pfd.fileDescriptor)
                    val tags = arrayOf(
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_DATETIME_DIGITIZED,
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION
                    )
                    for (tag in tags) {
                        srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                    }
                    dstExif.saveAttributes()
                }
            }
        } catch (e: Exception) {
            HdLogger.w(TAG, "Failed to copy EXIF metadata: ${e.message}")
        }
    }

    private suspend fun updateJobFailure(jobId: Long, errCode: String, errMsg: String) {
        if (jobId == 0L) return
        try {
            val job = jobDao.getJobById(jobId)
            if (job != null) {
                jobDao.updateJob(
                    job.copy(
                        status = "FAILED",
                        errorMessage = "$errCode: $errMsg",
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            HdLogger.e(TAG, "Failed to update job failure in Room: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "UpscaleImageUseCase"
    }
}
