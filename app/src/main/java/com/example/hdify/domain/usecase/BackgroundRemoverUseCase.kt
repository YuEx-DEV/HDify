package com.example.hdify.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdify.logging.HdLogger
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BackgroundRemoverUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "BgRemoverUseCase"
    }

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    /**
     * Generates an editable ARGB_8888 mask from ML Kit segmentation.
     * Uses high-precision multi-sample perimeter background detection, bilinear mask interpolation,
     * and edge-guided color snapping to eliminate jagged pixels and color bleeding.
     */
    suspend fun generateInitialMask(inputBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = inputBitmap.width
        val height = inputBitmap.height

        val inputImage = InputImage.fromBitmap(inputBitmap, 0)
        val mask: SegmentationMask = segmenter.process(inputImage).awaitTask()

        val maskWidth = mask.width
        val maskHeight = mask.height
        val maskBuffer = mask.buffer
        maskBuffer.rewind()
        val floatBuffer: FloatBuffer = maskBuffer.asFloatBuffer()

        val maskConfidences = FloatArray(maskWidth * maskHeight)
        floatBuffer.get(maskConfidences)

        val srcPixels = IntArray(width * height)
        inputBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // 1. Multi-point perimeter background sampling to handle gradients, multi-color or flat backdrops
        val bgPalette = sampleBorderPalette(srcPixels, width, height)
        val bgStrictDistSq = 26.0 * 26.0
        val bgRelaxedDistSq = 44.0 * 44.0

        val outPixels = IntArray(width * height)

        val xRatio = if (width > 1) (maskWidth - 1).toFloat() / (width - 1) else 0f
        val yRatio = if (height > 1) (maskHeight - 1).toFloat() / (height - 1) else 0f

        for (y in 0 until height) {
            val fy = y * yRatio
            val y0 = fy.toInt().coerceIn(0, maskHeight - 1)
            val y1 = (y0 + 1).coerceIn(0, maskHeight - 1)
            val wy = fy - y0

            val row0Offset = y0 * maskWidth
            val row1Offset = y1 * maskWidth
            val rowOffset = y * width

            for (x in 0 until width) {
                val fx = x * xRatio
                val x0 = fx.toInt().coerceIn(0, maskWidth - 1)
                val x1 = (x0 + 1).coerceIn(0, maskWidth - 1)
                val wx = fx - x0

                // High-precision Bilinear Interpolation of AI probability mask
                val c00 = maskConfidences[row0Offset + x0]
                val c10 = maskConfidences[row0Offset + x1]
                val c01 = maskConfidences[row1Offset + x0]
                val c11 = maskConfidences[row1Offset + x1]

                val c0 = c00 * (1f - wx) + c10 * wx
                val c1 = c01 * (1f - wx) + c11 * wx
                val confidence = c0 * (1f - wy) + c1 * wy

                val idx = rowOffset + x
                val pixelColor = srcPixels[idx]
                val bgDist = minDistanceToBg(pixelColor, bgPalette)

                // Edge-guided matting and anti-halo decision
                val alpha = when {
                    // Suppress exact border background matches
                    bgDist <= bgStrictDistSq && confidence < 0.90f -> 0
                    bgDist <= bgRelaxedDistSq && confidence < 0.65f -> 0
                    confidence >= 0.85f -> {
                        // High confidence foreground: remove edge halos
                        if (bgDist <= bgStrictDistSq * 0.7) 0 else 255
                    }
                    confidence <= 0.12f -> 0
                    else -> {
                        // Smoothstep transition curve with color boundary awareness
                        val t = ((confidence - 0.12f) / 0.73f).coerceIn(0f, 1f)
                        val smoothT = t * t * (3f - 2f * t)
                        if (bgDist <= bgRelaxedDistSq) {
                            0
                        } else {
                            (smoothT * 255).toInt().coerceIn(0, 255)
                        }
                    }
                }
                outPixels[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }

        Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun sampleBorderPalette(srcPixels: IntArray, width: Int, height: Int): List<Int> {
        val samples = ArrayList<Int>()
        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 12).coerceAtLeast(1)

        // Top and Bottom borders
        for (x in 0 until width step stepX) {
            samples.add(srcPixels[x])
            samples.add(srcPixels[(height - 1) * width + x])
        }
        // Left and Right borders
        for (y in 0 until height step stepY) {
            samples.add(srcPixels[y * width])
            samples.add(srcPixels[y * width + (width - 1)])
        }

        // Cluster and pick up to 8 representative background shades
        val distinct = ArrayList<Int>()
        val clusterThreshold = 32.0 * 32.0
        for (s in samples) {
            if (distinct.none { colorDistSq(it, s) < clusterThreshold }) {
                distinct.add(s)
                if (distinct.size >= 8) break
            }
        }
        return distinct
    }

    private fun minDistanceToBg(color: Int, bgPalette: List<Int>): Double {
        if (bgPalette.isEmpty()) return Double.MAX_VALUE
        var minDist = Double.MAX_VALUE
        for (bg in bgPalette) {
            val d = colorDistSq(color, bg)
            if (d < minDist) {
                minDist = d
            }
        }
        return minDist
    }

    private fun colorDistSq(c1: Int, c2: Int): Double {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF

        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val rMean = (r1 + r2) / 2
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()

        return (((512 + rMean) * dr * dr) / 256.0) + (4.0 * dg * dg) + (((767 - rMean) * db * db) / 256.0)
    }

    /**
     * Renders composite image from source bitmap and mask bitmap with target background color.
     */
    suspend fun renderComposite(
        sourceBitmap: Bitmap,
        maskBitmap: Bitmap,
        bgColor: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val srcPixels = IntArray(width * height)
        sourceBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)
        val isTransparent = (bgColor == Color.TRANSPARENT)
        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        for (i in 0 until width * height) {
            val srcP = srcPixels[i]
            val maskAlpha = (maskPixels[i] ushr 24) and 0xFF

            if (isTransparent) {
                val srcA = (srcP ushr 24) and 0xFF
                val finalA = (srcA * maskAlpha) / 255
                outPixels[i] = (finalA shl 24) or (srcP and 0x00FFFFFF)
            } else {
                val alphaFactor = maskAlpha / 255f
                val srcR = (srcP shr 16) and 0xFF
                val srcG = (srcP shr 8) and 0xFF
                val srcB = srcP and 0xFF

                val finalR = ((srcR * alphaFactor) + (bgR * (1f - alphaFactor))).toInt().coerceIn(0, 255)
                val finalG = ((srcG * alphaFactor) + (bgG * (1f - alphaFactor))).toInt().coerceIn(0, 255)
                val finalB = ((srcB * alphaFactor) + (bgB * (1f - alphaFactor))).toInt().coerceIn(0, 255)
                outPixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    suspend fun processBackgroundRemoval(
        inputBitmap: Bitmap,
        bgColor: Int
    ): Bitmap {
        val mask = generateInitialMask(inputBitmap)
        return renderComposite(inputBitmap, mask, bgColor)
    }

    /**
     * Saves resulting bitmap to MediaStore gallery.
     */
    suspend fun saveBitmapToGallery(
        bitmap: Bitmap,
        isTransparent: Boolean
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val timeStamp = System.currentTimeMillis()
        val extension = if (isTransparent) "png" else "jpg"
        val mimeType = if (isTransparent) "image/png" else "image/jpeg"
        val compressFormat = if (isTransparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val fileName = "HDify_cutout_${timeStamp}.$extension"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HDify")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Gagal membuat entri MediaStore untuk $fileName")

        var outputStream: OutputStream? = null
        try {
            outputStream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Gagal membuka stream output MediaStore")
            val quality = if (isTransparent) 100 else 95
            bitmap.compress(compressFormat, quality, outputStream)
            outputStream.flush()
        } finally {
            outputStream?.close()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        HdLogger.i(TAG, "Foto tersimpan ke galeri: $uri")
        uri
    }

    /**
     * Saves bitmap temporarily to cache so it can be passed to ImageUpscaleFragment as a file Uri.
     */
    suspend fun saveTempBitmapForUpscale(
        bitmap: Bitmap
    ): Uri = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "bg_remover_temp")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "cutout_upscale_${System.currentTimeMillis()}.png")

        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        Uri.fromFile(tempFile)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
