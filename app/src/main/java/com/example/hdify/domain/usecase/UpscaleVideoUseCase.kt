package com.example.hdify.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import com.example.hdify.data.preferences.UserPreferencesRepository
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineType
import com.example.hdify.engine.UpscaleEngineFactory
import com.example.hdify.logging.BreadcrumbManager
import com.example.hdify.logging.HdLogger
import com.example.hdify.video.VideoCapabilitiesHelper
import com.example.hdify.video.VideoMetadataExtractor
import com.example.hdify.video.VideoPipelineProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class VideoUpscaleRequest(
    val sourceUri: Uri,
    val scale: Int = 1,
    val engineType: EngineType = EngineType.REAL_ESRGAN,
    val modelDir: String = "realesr-animevideov3-x4",
    val isHevc: Boolean = true,
    val qualityPercent: Int = 85,
    val isPreview3s: Boolean = false,
    val interpolationFactor: Int = 1,
    val rifeModelDir: String = "models/rife/rife-v4.6",
    val maxFps: Int = 120,
    val uhdMode: Boolean = false,
    val existingJobId: Long = 0L
)

sealed class VideoUpscaleStatus {
    data class Progress(
        val currentFrame: Int,
        val totalFrames: Int,
        val percent: Int,
        val fps: Double,
        val etaSeconds: Long
    ) : VideoUpscaleStatus()

    data class Success(
        val outputUri: Uri,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val isPreview: Boolean,
        val tempFilePath: String? = null,
        val isSaved: Boolean = false
    ) : VideoUpscaleStatus()

    object Canceled : VideoUpscaleStatus()
    data class Error(val code: String, val message: String) : VideoUpscaleStatus()
}

@Singleton
class UpscaleVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineFactory: UpscaleEngineFactory,
    private val jobDao: JobDao,
    private val prefsRepo: UserPreferencesRepository
) {
    private var activeProcessor: VideoPipelineProcessor? = null
    private val dbScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    suspend fun execute(
        request: VideoUpscaleRequest,
        onProgress: (VideoUpscaleStatus.Progress) -> Unit
    ): VideoUpscaleStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var jobId = 0L
        var upscaleEngine: com.example.hdify.engine.UpscaleEngine? = null
        var interpolationEngine: com.example.hdify.engine.InterpolationEngine? = null

        try {
            if (request.scale <= 1 && request.interpolationFactor <= 1) {
                return@withContext VideoUpscaleStatus.Error(
                    "HD-REQ-001",
                    "Pilih minimal salah satu fitur: Upscale (> 1x) atau Interpolasi Frame (x2/x3/x4)"
                )
            }

            val metadata = VideoMetadataExtractor.extract(context, request.sourceUri)
                ?: return@withContext VideoUpscaleStatus.Error("HD-VID-401", "Gagal membaca metadata video")

            val targetWidth = metadata.width * request.scale
            val targetHeight = metadata.height * request.scale

            // 1. Check free storage space (FR-VID-11)
            val interpMultiplier = if (request.interpolationFactor > 1) 1.5 else 1.0
            val estimatedBytes = ((metadata.durationUs / 1_000_000L) * (metadata.bitrate * request.scale * interpMultiplier / 8)).toLong() + 100_000_000L
            val availableSpace = context.cacheDir.usableSpace
            if (availableSpace < estimatedBytes && !request.isPreview3s) {
                return@withContext VideoUpscaleStatus.Error(
                    "HD-STO-602",
                    "Penyimpanan tidak mencukupi (Tersedia: ${availableSpace / 1_000_000}MB, Diperlukan: ~${estimatedBytes / 1_000_000}MB)"
                )
            }

            // 2. Insert Room job
            val engineNameStr = buildString {
                if (request.scale > 1) {
                    append(if (request.engineType == EngineType.REAL_CUGAN) "Real-CUGAN" else "Real-ESRGAN")
                    append(" (${request.modelDir})")
                }
                if (request.interpolationFactor > 1) {
                    if (isNotEmpty()) append(" + ")
                    append("RIFE (${request.rifeModelDir} x${request.interpolationFactor})")
                }
                if (isEmpty()) {
                    append("RIFE Frame Interpolation")
                }
            }

            if (!request.isPreview3s) {
                jobId = if (request.existingJobId > 0L) {
                    request.existingJobId
                } else {
                    jobDao.insertJob(
                        JobEntity(
                            mediaType = "VIDEO",
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
            }

            BreadcrumbManager.add("Video Job #$jobId: Init pipeline scale=x${request.scale}, interp=x${request.interpolationFactor}")
            HdLogger.i(TAG, "[job=$jobId stage=INIT] Video pipeline target=${targetWidth}x${targetHeight} interp=x${request.interpolationFactor} hevc=${request.isHevc}")

            val allowCpu = prefsRepo.allowCpuFallbackFlow.first()

            // 3. Init Upscale Engine if scale > 1
            if (request.scale > 1) {
                val effectiveModelDir = if (request.modelDir.startsWith("realesr-animevideov3")) {
                    "realesr-animevideov3-x${request.scale}"
                } else {
                    request.modelDir
                }
                val engine = engineFactory.getEngine(request.engineType)
                val engineConfig = EngineConfig(
                    engine = request.engineType,
                    modelDirectory = effectiveModelDir,
                    scale = request.scale,
                    denoise = 0,
                    tileSize = 0,
                    useFp16 = true,
                    gpuIndex = 0,
                    allowCpuFallback = allowCpu
                )
                engine.init(context.assets, engineConfig)
                upscaleEngine = engine
            }

            // 4. Init RIFE Interpolation Engine if interpolationFactor > 1
            if (request.interpolationFactor > 1) {
                val rife = com.example.hdify.engine.RifeInterpolationEngine()
                rife.init(
                    assetManager = context.assets,
                    modelDir = request.rifeModelDir,
                    gpuIndex = 0,
                    useFp16 = true,
                    uhdMode = request.uhdMode,
                    allowCpuFallback = allowCpu
                )
                interpolationEngine = rife
            }

            // 5. Temporary output file
            val tempFile = File(context.cacheDir, "hdify_temp_video_${System.currentTimeMillis()}.mp4")

            // 6. Setup Pipeline Processor
            val processor = VideoPipelineProcessor(
                context = context,
                sourceUri = request.sourceUri,
                outputFile = tempFile,
                scale = request.scale,
                isHevc = request.isHevc,
                qualityPercent = request.qualityPercent,
                upscaleEngine = upscaleEngine,
                interpolationEngine = interpolationEngine,
                interpolationFactor = request.interpolationFactor,
                maxFps = request.maxFps,
                isPreview3s = request.isPreview3s
            )
            activeProcessor = processor

            var lastDbUpdateMs = 0L

            val completed = processor.process { current, total, percent, fps, etaSec ->
                onProgress(VideoUpscaleStatus.Progress(current, total, percent, fps, etaSec))

                val now = System.currentTimeMillis()
                if (jobId > 0 && (now - lastDbUpdateMs > 250 || percent == 100)) {
                    lastDbUpdateMs = now
                    dbScope.launch {
                        try {
                            jobDao.getJobById(jobId)?.let { job ->
                                if (job.status == "PROCESSING") {
                                    jobDao.updateJob(job.copy(progress = percent, checkpointFrame = current, totalFrames = total))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (!completed) {
                if (jobId > 0) {
                    jobDao.getJobById(jobId)?.let { job ->
                        jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                    }
                }
                return@withContext VideoUpscaleStatus.Canceled
            }

            // 6. Keep processed video in draft file (cacheDir) so user can adjust rotation & mirror before saving
            val displayW = metadata.width * request.scale
            val displayH = metadata.height * request.scale
            val durationMs = System.currentTimeMillis() - startTime

            val draftFile = File(context.cacheDir, "hdify_draft_${System.currentTimeMillis()}.mp4")
            tempFile.copyTo(draftFile, overwrite = true)
            try { tempFile.delete() } catch (_: Throwable) {}

            val draftUri = Uri.fromFile(draftFile)

            if (jobId > 0) {
                jobDao.getJobById(jobId)?.let { job ->
                    jobDao.updateJob(
                        job.copy(
                            outputUri = draftUri.toString(),
                            status = "COMPLETED",
                            progress = 100,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            HdLogger.i(TAG, "[job=$jobId stage=DONE] Video upscale finished draft: $draftUri in ${durationMs}ms")
            VideoUpscaleStatus.Success(
                outputUri = draftUri,
                width = displayW,
                height = displayH,
                durationMs = durationMs,
                isPreview = request.isPreview3s,
                tempFilePath = draftFile.absolutePath,
                isSaved = false
            )

        } catch (ce: CancellationException) {
            HdLogger.i(TAG, "[job=$jobId stage=CANCEL] Video upscale coroutine cancelled")
            if (jobId > 0) {
                jobDao.getJobById(jobId)?.let { job ->
                    jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                }
            }
            VideoUpscaleStatus.Canceled
        } catch (t: Throwable) {
            val errCode = if (t.message?.startsWith("HD-") == true) t.message!!.substringBefore(":") else "HD-VID-403"
            val errMsg = t.message ?: "Video upscale processing failed"
            HdLogger.e(TAG, "[job=$jobId stage=ERROR] $errCode $errMsg", t)

            if (jobId > 0) {
                jobDao.getJobById(jobId)?.let { job ->
                    jobDao.updateJob(
                        job.copy(
                            status = "FAILED",
                            errorMessage = "$errCode: $errMsg",
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            VideoUpscaleStatus.Error(errCode, errMsg)
        } finally {
            withContext(NonCancellable) {
                try {
                    upscaleEngine?.release()
                    interpolationEngine?.release()
                } catch (e: Exception) {
                    HdLogger.w(TAG, "Error releasing engines: ${e.message}")
                }
                activeProcessor = null
                com.example.hdify.service.UpscaleForegroundService.currentActiveJobId = 0L
            }
        }
    }

    fun pause() {
        activeProcessor?.pause()
    }

    fun resume() {
        activeProcessor?.resume()
    }

    fun cancel() {
        activeProcessor?.cancel()
        try {
            engineFactory.getEngine(EngineType.REAL_CUGAN).cancel()
            engineFactory.getEngine(EngineType.REAL_ESRGAN).cancel()
        } catch (e: Exception) {
            HdLogger.w(TAG, "Error cancelling engines in video: ${e.message}")
        }
    }

    private fun saveVideoToMediaStore(tempFile: File, width: Int, height: Int, isPreview: Boolean): Uri {
        val filename = if (isPreview) {
            "HDify_Preview_${System.currentTimeMillis()}.mp4"
        } else {
            "HDify_Video_${width}x${height}_${System.currentTimeMillis()}.mp4"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/HDify")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("HD-STO-601 Failed to create MediaStore video record")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tempFile).use { inStream ->
                    inStream.copyTo(out)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            tempFile.delete()
            return uri
        } else {
            val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HDify")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val destFile = File(moviesDir, filename)
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            return Uri.fromFile(destFile)
        }
    }

    companion object {
        private const val TAG = "UpscaleVideoUseCase"
    }
}
