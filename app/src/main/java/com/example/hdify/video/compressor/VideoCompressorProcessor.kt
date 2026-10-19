package com.example.hdify.video.compressor

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdify.logging.HdLogger
import com.example.hdify.video.VideoMetadata
import com.example.hdify.video.gl.VideoTransformSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoCompressorProcessor {

    private val isCancelled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val pauseLock = Object()

    fun cancel() {
        isCancelled.set(true)
        resume()
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        synchronized(pauseLock) {
            isPaused.set(false)
            pauseLock.notifyAll()
        }
    }

    fun isPaused(): Boolean = isPaused.get()

    suspend fun compress(
        context: Context,
        sourceUri: Uri,
        meta: VideoMetadata,
        config: VideoCompressorConfig,
        onProgress: (currentFrame: Int, totalFrames: Int, percent: Int, fps: Double, etaSec: Long) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        isCancelled.set(false)
        isPaused.set(false)

        val tempOutputFile = File(context.cacheDir, "hdify_compress_${System.currentTimeMillis()}.mp4")

        val videoExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
        var audioExtractor: MediaExtractor? = null

        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var transformSurface: VideoTransformSurface? = null
        var isMuxerStarted = false

        try {
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            if (videoTrackIndex < 0) throw IllegalStateException("HD-CMP-101 No video track found in source")
            videoExtractor.selectTrack(videoTrackIndex)
            val inputVideoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

            val audioTrackIndex = selectTrack(videoExtractor, "audio/")
            if (audioTrackIndex >= 0) {
                audioExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
                audioExtractor.selectTrack(audioTrackIndex)
            }

            var targetMime = config.mimeType
            encoder = try {
                MediaCodec.createEncoderByType(targetMime)
            } catch (t: Throwable) {
                if (targetMime != MediaFormat.MIMETYPE_VIDEO_AVC) {
                    targetMime = MediaFormat.MIMETYPE_VIDEO_AVC
                    MediaCodec.createEncoderByType(targetMime)
                } else {
                    throw t
                }
            }

            val outputVideoFormat = MediaFormat.createVideoFormat(targetMime, config.targetWidth, config.targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps.toInt())
                setFloat(MediaFormat.KEY_FRAME_RATE, config.targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                try {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                } catch (_: Throwable) {}

                // Priority & Complexity for high-fidelity rate-distortion optimization
                try {
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                } catch (_: Throwable) {}
                try {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 2)
                } catch (_: Throwable) {}

                if (targetMime == MediaFormat.MIMETYPE_VIDEO_AVC) {
                    val maxDim = maxOf(config.targetWidth, config.targetHeight)
                    val avcLevel = when {
                        maxDim > 1920 || (maxDim == 1920 && config.targetFps > 30f) -> MediaCodecInfo.CodecProfileLevel.AVCLevel51
                        maxDim >= 1280 -> MediaCodecInfo.CodecProfileLevel.AVCLevel42
                        else -> MediaCodecInfo.CodecProfileLevel.AVCLevel31
                    }
                    try {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                        setInteger(MediaFormat.KEY_LEVEL, avcLevel)
                    } catch (_: Throwable) {}
                } else if (targetMime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                    try {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                    } catch (_: Throwable) {}
                }
            }

            try {
                encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (cfgEx: Exception) {
                HdLogger.w(TAG, "Encoder configure failed with High profile: ${cfgEx.message}. Retrying Main profile...")
                try {
                    val mainFormat = MediaFormat.createVideoFormat(targetMime, config.targetWidth, config.targetHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps.toInt())
                        setFloat(MediaFormat.KEY_FRAME_RATE, config.targetFps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                        try {
                            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                        } catch (_: Throwable) {}
                        if (targetMime == MediaFormat.MIMETYPE_VIDEO_AVC) {
                            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
                        }
                    }
                    encoder.configure(mainFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } catch (cfgEx2: Exception) {
                    HdLogger.w(TAG, "Encoder configure failed with Main profile: ${cfgEx2.message}. Retrying basic format...")
                    val basicFormat = MediaFormat.createVideoFormat(targetMime, config.targetWidth, config.targetHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps.toInt())
                        setFloat(MediaFormat.KEY_FRAME_RATE, config.targetFps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                    }
                    encoder.configure(basicFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            }

            val encoderInputSurface = encoder.createInputSurface()

            transformSurface = VideoTransformSurface(
                encoderInputSurface = encoderInputSurface,
                outWidth = config.targetWidth,
                outHeight = config.targetHeight,
                rotationDegrees = meta.rotationDegrees,
                isMirrored = false,
                forceHdr = config.forceHdr10Plus
            )

            val decoderMime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(decoderMime).apply {
                configure(inputVideoFormat, transformSurface.decoderSurface, null, 0)
                start()
            }
            encoder.start()

            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1

            if (audioExtractor != null && audioTrackIndex >= 0) {
                val inputAudioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                try {
                    muxerAudioTrack = muxer.addTrack(inputAudioFormat)
                    HdLogger.i(TAG, "Audio track added to compressor muxer: index=$muxerAudioTrack")
                } catch (t: Throwable) {
                    try {
                        val mime = inputAudioFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_AUDIO_AAC
                        val channels = inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val sampleRate = inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val cleanFormat = MediaFormat.createAudioFormat(mime, sampleRate, channels)
                        if (inputAudioFormat.containsKey("csd-0")) {
                            cleanFormat.setByteBuffer("csd-0", inputAudioFormat.getByteBuffer("csd-0"))
                        }
                        if (inputAudioFormat.containsKey("csd-1")) {
                            cleanFormat.setByteBuffer("csd-1", inputAudioFormat.getByteBuffer("csd-1"))
                        }
                        muxerAudioTrack = muxer.addTrack(cleanFormat)
                        HdLogger.i(TAG, "Sanitized audio track added to compressor muxer: index=$muxerAudioTrack")
                    } catch (t2: Throwable) {
                        HdLogger.w(TAG, "Audio track could not be added to compressor muxer: ${t2.message}")
                        muxerAudioTrack = -1
                    }
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val kTimeoutUs = 8_000L
            var isExtractorEos = false
            var isDecoderEos = false
            var isEncoderEos = false
            var decoderDrainTries = 0
            var eosDrainTries = 0

            var framesProcessed = 0
            val totalDurationUs = meta.durationUs.coerceAtLeast(1_000_000L)
            val estTotalFrames = if (meta.totalFrames > 0) meta.totalFrames.toInt() else ((meta.durationMs * config.targetFps) / 1000).toInt().coerceAtLeast(1)

            HdLogger.i(TAG, "Starting hardware video compression: ${meta.displayWidth}x${meta.displayHeight} -> ${config.targetWidth}x${config.targetHeight}, bitrate=${config.bitrateBps / 1000}kbps, codec=$targetMime")

            while (!isEncoderEos && !isCancelled.get()) {
                synchronized(pauseLock) {
                    while (isPaused.get() && !isCancelled.get()) {
                        pauseLock.wait(500)
                    }
                }
                if (isCancelled.get()) break

                // Feed Extractor -> Decoder
                if (!isExtractorEos) {
                    val inIndex = decoder.dequeueInputBuffer(kTimeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = videoExtractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEos = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, videoExtractor.sampleTime, videoExtractor.sampleFlags)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                // Drain Decoder -> Render to Transform Surface
                if (!isDecoderEos) {
                    val decIndex = decoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                    if (decIndex >= 0) {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val render = bufferInfo.size > 0 && !isEos

                        decoder.releaseOutputBuffer(decIndex, render)
                        if (render) {
                            transformSurface.drawFrame(ptsNs = bufferInfo.presentationTimeUs * 1000L)
                            framesProcessed++

                            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.01)
                            val currentFps = framesProcessed / elapsedSec
                            val progressPercent = ((bufferInfo.presentationTimeUs.toDouble() / totalDurationUs.toDouble()) * 100).toInt().coerceIn(0, 99)
                            val remainingFrames = (estTotalFrames - framesProcessed).coerceAtLeast(0)
                            val etaSec = if (currentFps > 0) (remainingFrames / currentFps).toLong() else 0L

                            if (framesProcessed % 5 == 0) {
                                onProgress(framesProcessed, estTotalFrames, progressPercent, currentFps, etaSec)
                            }
                        }

                        if (isEos) {
                            isDecoderEos = true
                            try {
                                encoder.signalEndOfInputStream()
                            } catch (_: Throwable) {}
                        }
                    } else if (decIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (isExtractorEos) {
                            decoderDrainTries++
                            if (decoderDrainTries >= 30) {
                                isDecoderEos = true
                                try {
                                    encoder.signalEndOfInputStream()
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                // Drain Encoder -> Muxer
                var encIndex = encoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
                    muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    isMuxerStarted = true
                    HdLogger.i(TAG, "Compressor muxer started: videoTrack=$muxerVideoTrack")
                }

                if (encIndex < 0 && isDecoderEos) {
                    eosDrainTries++
                    if (eosDrainTries >= 35) {
                        isEncoderEos = true
                    }
                } else if (encIndex >= 0) {
                    eosDrainTries = 0
                }

                while (encIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && isMuxerStarted) {
                        val encodedBuffer = encoder.getOutputBuffer(encIndex)
                        if (encodedBuffer != null) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encodedBuffer, bufferInfo)
                        }
                    }

                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(encIndex, false)
                    if (isEos) {
                        isEncoderEos = true
                        break
                    }
                    encIndex = encoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                    if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                }
            }

            if (isCancelled.get()) {
                throw InterruptedException("Kompresi dibatalkan oleh pengguna")
            }

            // Audio passthrough copy
            if (muxerAudioTrack >= 0 && audioExtractor != null && isMuxerStarted) {
                try {
                    HdLogger.i(TAG, "Starting audio passthrough in compressor...")
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioSamplesCopied = 0

                    while (!isCancelled.get()) {
                        audioBuffer.clear()
                        val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                        if (sampleSize < 0) break

                        val sampleTime = audioExtractor.sampleTime
                        audioBuffer.position(0)
                        audioBuffer.limit(sampleSize)

                        audioBufferInfo.offset = 0
                        audioBufferInfo.size = sampleSize
                        audioBufferInfo.presentationTimeUs = sampleTime
                        audioBufferInfo.flags = audioExtractor.sampleFlags

                        muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                        audioSamplesCopied++
                        audioExtractor.advance()
                    }
                    HdLogger.i(TAG, "Compressor audio passthrough finished: $audioSamplesCopied samples copied")
                } catch (t: Throwable) {
                    HdLogger.w(TAG, "Audio passthrough warning: ${t.message}")
                }
            }

            // 1. Release decoder & encoder
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            decoder = null

            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            encoder = null

            try { transformSurface?.release() } catch (_: Throwable) {}
            transformSurface = null

            // 2. Stop and finalize MediaMuxer so the MP4 header (moov atom) is written to file
            if (isMuxerStarted) {
                try {
                    muxer.stop()
                    HdLogger.i(TAG, "Compressor muxer stopped successfully. File size: ${tempOutputFile.length()} bytes")
                } catch (muxEx: Throwable) {
                    HdLogger.e(TAG, "Error stopping compressor muxer: ${muxEx.message}", muxEx)
                }
                try { muxer.release() } catch (_: Throwable) {}
                muxer = null
                isMuxerStarted = false
            }

            try { videoExtractor.release() } catch (_: Throwable) {}
            try { audioExtractor?.release() } catch (_: Throwable) {}
            audioExtractor = null

            if (isCancelled.get()) {
                throw InterruptedException("Kompresi dibatalkan oleh pengguna")
            }

            // 3. Verify that the output MP4 file exists and has valid content
            if (!tempOutputFile.exists() || tempOutputFile.length() < 1024) {
                throw IllegalStateException("HD-CMP-501 File kompresi video tidak valid atau kosong (${tempOutputFile.length()} bytes)")
            }

            val finalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.01)
            val finalFps = framesProcessed / finalElapsedSec
            onProgress(framesProcessed, framesProcessed, 100, finalFps, 0L)

            // 4. Save the finalized, fully-closed MP4 file to MediaStore
            val finalUri = saveToMediaStore(context, tempOutputFile, config.targetWidth, config.targetHeight, config.preset)
            try { tempOutputFile.delete() } catch (_: Throwable) {}

            HdLogger.i(TAG, "Compression completed in ${(System.currentTimeMillis() - startTime)}ms. Output saved: $finalUri")
            return@withContext finalUri

        } finally {
            try { transformSurface?.release() } catch (_: Throwable) {}
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { if (isMuxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { videoExtractor.release() } catch (_: Throwable) {}
            try { audioExtractor?.release() } catch (_: Throwable) {}

            if ((isCancelled.get() || tempOutputFile.exists()) && tempOutputFile.length() == 0L) {
                try { tempOutputFile.delete() } catch (_: Throwable) {}
            }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    private fun saveToMediaStore(
        context: Context,
        file: File,
        width: Int,
        height: Int,
        preset: CompressPreset
    ): Uri {
        val presetTag = preset.name.lowercase()
        val filename = "HDify_${presetTag}_${width}x${height}_${System.currentTimeMillis()}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/HDify_Compressed")
                put(MediaStore.Video.VideoColumns.WIDTH, width)
                put(MediaStore.Video.VideoColumns.HEIGHT, height)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore video record for compressed video")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { inStream ->
                    inStream.copyTo(out)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HDify_Compressed")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val destFile = File(moviesDir, filename)
            file.copyTo(destFile, overwrite = true)
            return Uri.fromFile(destFile)
        }
    }

    companion object {
        private const val TAG = "VideoCompressor"
    }
}
