package com.example.hdify.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.hdify.engine.InterpolationEngine
import com.example.hdify.engine.UpscaleEngine
import com.example.hdify.logging.HdLogger
import com.example.hdify.video.gl.DecoderSurface
import com.example.hdify.video.gl.EncoderSurface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoPipelineProcessor(
    private val context: Context,
    private val sourceUri: Uri,
    private val outputFile: File,
    private val scale: Int,
    private val isHevc: Boolean,
    private val qualityPercent: Int,
    private val upscaleEngine: UpscaleEngine?,
    private val interpolationEngine: InterpolationEngine? = null,
    private val interpolationFactor: Int = 1,
    private val maxFps: Int = 120,
    private val isPreview3s: Boolean = false
) {
    private val isCancelled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val pauseLock = Object()

    fun cancel() {
        isCancelled.set(true)
        try {
            upscaleEngine?.cancel()
            interpolationEngine?.cancel()
        } catch (_: Exception) {}
        resumeIfPaused()
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
        resumeIfPaused()
    }

    private fun resumeIfPaused() {
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }

    fun process(
        onProgress: (currentFrame: Int, totalFrames: Int, percent: Int, fps: Double, etaSeconds: Long) -> Unit
    ): Boolean {
        HdLogger.i(TAG, "Starting video pipeline: scale=x$scale, interp=x$interpolationFactor, hevc=$isHevc, preview3s=$isPreview3s")

        val metadata = VideoMetadataExtractor.extract(context, sourceUri)
            ?: throw IllegalStateException("HD-VID-401 Failed to read video metadata")

        val targetWidth = metadata.width * scale
        val targetHeight = metadata.height * scale
        val encoderMime = if (isHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        // Target FPS with maxFps bounding
        val rawTargetFps = metadata.fps * interpolationFactor
        val targetFps = rawTargetFps.coerceAtMost(maxFps.toFloat())
        val actualFactor = if (metadata.fps > 0) (targetFps / metadata.fps).toInt().coerceAtLeast(1) else interpolationFactor

        // Check encoder capability
        val encCap = VideoCapabilitiesHelper.findEncoder(encoderMime)
            ?: throw IllegalStateException("HD-ENC-501 No hardware encoder found for $encoderMime")

        val targetBitrate = VideoCapabilitiesHelper.calculateAdaptiveBitrate(
            targetWidth, targetHeight, targetFps.toFloat(), isHevc, qualityPercent
        )

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var decoderSurface: DecoderSurface? = null
        var encoderSurface: EncoderSurface? = null
        var heapInBitmap: Bitmap? = null
        var heapPrevBitmap: Bitmap? = null
        var heapInterpBitmap: Bitmap? = null
        var heapOutBitmap: Bitmap? = null

        val startTime = System.currentTimeMillis()
        var processedFrames = 0
        val totalSourceFrames = if (isPreview3s) {
            ((metadata.fps * 3).toInt()).coerceAtLeast(1)
        } else {
            metadata.totalFrames.toInt().coerceAtLeast(1)
        }
        val targetTotalFrames = totalSourceFrames * actualFactor

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            if (videoTrackIndex < 0) {
                throw IllegalStateException("HD-VID-402 No video track found")
            }
            videoExtractor.selectTrack(videoTrackIndex)
            val inputVideoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

            // Setup audio extractor for passthrough
            var audioTrackIndex = -1
            if (metadata.hasAudio) {
                audioExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
                audioTrackIndex = selectTrack(audioExtractor, "audio/")
                if (audioTrackIndex >= 0) {
                    audioExtractor.selectTrack(audioTrackIndex)
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    HdLogger.i(TAG, "Audio track selected: index=$audioTrackIndex, mime=${metadata.audioMimeType}")
                }
            }

            // 1. Decoder Surface & Decoder
            decoderSurface = DecoderSurface(metadata.width, metadata.height)
            val decoderMime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(decoderMime).apply {
                configure(inputVideoFormat, decoderSurface.surface, null, 0)
                start()
            }

            // 2. Encoder & Encoder Surface
            val outputVideoFormat = MediaFormat.createVideoFormat(encoderMime, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setFloat(MediaFormat.KEY_FRAME_RATE, targetFps.toFloat())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                // Configure Variable Bitrate (VBR) mode to eliminate macroblocking / bitrate breakdown
                try {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                } catch (t: Throwable) {
                    HdLogger.w(TAG, "BITRATE_MODE_VBR not supported by device, fallback to default")
                }

                // Profile configuration for high-fidelity edge definition
                try {
                    if (encoderMime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                    } else if (encoderMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    }
                } catch (t: Throwable) {
                    HdLogger.w(TAG, "Encoder profile configuration skipped: ${t.message}")
                }
            }
            encoder = MediaCodec.createByCodecName(encCap.codecName).apply {
                configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = encoder.createInputSurface()
            encoderSurface = EncoderSurface(inputSurface, targetWidth, targetHeight)
            encoder.start()

            // 3. MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                if (metadata.rotationDegrees != 0) {
                    setOrientationHint(metadata.rotationDegrees)
                }
            }

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            isMuxerStarted = false

            // Add audio track before muxer start if available
            if (audioExtractor != null && audioTrackIndex >= 0) {
                val inputAudioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                try {
                    muxerAudioTrack = muxer.addTrack(inputAudioFormat)
                    HdLogger.i(TAG, "Audio track added to muxer: index=$muxerAudioTrack")
                } catch (t: Throwable) {
                    HdLogger.w(TAG, "Direct addTrack for audio failed: ${t.message}. Attempting sanitized audio format...")
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
                        if (inputAudioFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            cleanFormat.setInteger(MediaFormat.KEY_BIT_RATE, inputAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE))
                        }
                        muxerAudioTrack = muxer.addTrack(cleanFormat)
                        HdLogger.i(TAG, "Sanitized audio track added to muxer: index=$muxerAudioTrack")
                    } catch (t2: Throwable) {
                        HdLogger.e(TAG, "Sanitized addTrack also failed: ${t2.message}. Continuing with video-only.", t2)
                        muxerAudioTrack = -1
                    }
                }
            }

            val inBitmap = Bitmap.createBitmap(metadata.width, metadata.height, Bitmap.Config.ARGB_8888)
            val prevBitmap = if (actualFactor > 1) Bitmap.createBitmap(metadata.width, metadata.height, Bitmap.Config.ARGB_8888) else null
            val interpBitmap = if (actualFactor > 1) Bitmap.createBitmap(metadata.width, metadata.height, Bitmap.Config.ARGB_8888) else null
            val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            heapInBitmap = inBitmap
            heapPrevBitmap = prevBitmap
            heapInterpBitmap = interpBitmap
            val canvasOut = Canvas(outBitmap)
            val canvasPrev = prevBitmap?.let { Canvas(it) }
            val copyPaint = Paint()

            var isFirstFrame = true
            var lastPtsUs = 0L
            var lastEmittedPtsUs = -1L
            val idealFrameDurationUs = (1_000_000.0 / targetFps.toDouble()).toLong().coerceAtLeast(1000L)

            val bufferInfo = MediaCodec.BufferInfo()
            val kTimeoutUs = 10_000L
            var isExtractorEos = false
            var isDecoderEos = false
            var isEncoderEos = false
            var eosDrainTries = 0

            var decoderDrainTries = 0

            while (!isEncoderEos && !isCancelled.get()) {
                // Handle Pause
                synchronized(pauseLock) {
                    while (isPaused.get() && !isCancelled.get()) {
                        pauseLock.wait(500)
                    }
                }
                if (isCancelled.get()) break

                // Feed input to decoder
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
                                val sampleTime = videoExtractor.sampleTime
                                val isReachPreviewEnd = isPreview3s && sampleTime >= 3_000_000L
                                val flags = if (isReachPreviewEnd) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, flags)

                                if (isReachPreviewEnd) {
                                    isExtractorEos = true
                                } else {
                                    videoExtractor.advance()
                                }
                            }
                        }
                    }
                }

                // Drain decoder & process frame
                if (!isDecoderEos) {
                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                    when {
                        outIndex >= 0 -> {
                            decoderDrainTries = 0
                            val doRender = bufferInfo.size > 0 && !isCancelled.get()
                            decoder.releaseOutputBuffer(outIndex, doRender)

                            if (doRender) {
                                if (isCancelled.get()) break
                                decoderSurface.awaitNewImage()
                                decoderSurface.drawImageToBitmap(inBitmap)

                                val currentPtsUs = bufferInfo.presentationTimeUs

                                if (isFirstFrame || actualFactor <= 1 || interpolationEngine == null) {
                                    if (isCancelled.get()) break
                                    // Process first or non-interpolated frame
                                    if (upscaleEngine != null && scale > 1) {
                                        val res = upscaleEngine.upscale(inBitmap, outBitmap, null)
                                        if (res is com.example.hdify.engine.UpscaleResult.Canceled) {
                                            isCancelled.set(true)
                                            break
                                        }
                                        if (res is com.example.hdify.engine.UpscaleResult.Error) {
                                            throw RuntimeException("${res.code}: ${res.message}")
                                        }
                                    } else {
                                        canvasOut.drawBitmap(inBitmap, 0f, 0f, copyPaint)
                                    }

                                    if (isCancelled.get()) break
                                    val emitPtsUs = if (lastEmittedPtsUs < 0) currentPtsUs else maxOf(currentPtsUs, lastEmittedPtsUs + 1000L)
                                    encoderSurface.drawBitmap(outBitmap, emitPtsUs * 1000L)
                                    lastEmittedPtsUs = emitPtsUs
                                    lastPtsUs = currentPtsUs
                                    isFirstFrame = false
                                    processedFrames++

                                    canvasPrev?.drawBitmap(inBitmap, 0f, 0f, copyPaint)

                                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                                    val currentFps = processedFrames / elapsedSec
                                    val percent = if (targetTotalFrames > 0) ((processedFrames * 100) / targetTotalFrames).coerceIn(0, 99) else 50
                                    val remainingFrames = (targetTotalFrames - processedFrames).coerceAtLeast(0)
                                    val etaSec = if (currentFps > 0) (remainingFrames / currentFps).toLong() else 0L
                                    onProgress(processedFrames, targetTotalFrames, percent, currentFps, etaSec)
                                } else {
                                    // Interpolate intermediate frames between prevBitmap and inBitmap (FR-INT-01, FR-INT-03, FR-INT-04)
                                    if (currentPtsUs > lastPtsUs) {
                                        val deltaPtsUs = currentPtsUs - lastPtsUs
                                        val stepDurationUs = deltaPtsUs.toDouble() / actualFactor.toDouble()

                                        for (step in 1 until actualFactor) {
                                            if (isCancelled.get()) break

                                            val timestep = step.toFloat() / actualFactor.toFloat()
                                            val rawInterpPtsUs = lastPtsUs + (stepDurationUs * step).toLong()
                                            val maxAllowedInterpPtsUs = currentPtsUs - 500L.coerceAtMost(deltaPtsUs / (actualFactor * 2))
                                            val interpPtsUs = rawInterpPtsUs.coerceIn(lastEmittedPtsUs + 1000L, maxAllowedInterpPtsUs)

                                            val interpRes = interpolationEngine.interpolate(
                                                prevBitmap!!,
                                                inBitmap,
                                                timestep,
                                                interpBitmap!!,
                                                checkSceneCut = true,
                                                sceneCutThreshold = 0.55f
                                            )
                                            if (interpRes is com.example.hdify.engine.InterpolationResult.Canceled) {
                                                isCancelled.set(true)
                                                break
                                            }
                                            if (interpRes is com.example.hdify.engine.InterpolationResult.Error) {
                                                throw RuntimeException("${interpRes.code}: ${interpRes.message}")
                                            }

                                            // Upscale intermediate frame if upscale is requested
                                            if (upscaleEngine != null && scale > 1) {
                                                val res = upscaleEngine.upscale(interpBitmap, outBitmap, null)
                                                if (res is com.example.hdify.engine.UpscaleResult.Canceled) {
                                                    isCancelled.set(true)
                                                    break
                                                }
                                                if (res is com.example.hdify.engine.UpscaleResult.Error) {
                                                    throw RuntimeException("${res.code}: ${res.message}")
                                                }
                                            } else {
                                                canvasOut.drawBitmap(interpBitmap, 0f, 0f, copyPaint)
                                            }

                                            if (isCancelled.get()) break
                                            encoderSurface.drawBitmap(outBitmap, interpPtsUs * 1000L)
                                            lastEmittedPtsUs = interpPtsUs
                                            processedFrames++

                                            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                                            val currentFps = processedFrames / elapsedSec
                                            val percent = if (targetTotalFrames > 0) ((processedFrames * 100) / targetTotalFrames).coerceIn(0, 99) else 50
                                            val remainingFrames = (targetTotalFrames - processedFrames).coerceAtLeast(0)
                                            val etaSec = if (currentFps > 0) (remainingFrames / currentFps).toLong() else 0L
                                            onProgress(processedFrames, targetTotalFrames, percent, currentFps, etaSec)
                                        }
                                    }

                                    if (isCancelled.get()) break

                                    // Render current original frame
                                    if (upscaleEngine != null && scale > 1) {
                                        val res = upscaleEngine.upscale(inBitmap, outBitmap, null)
                                        if (res is com.example.hdify.engine.UpscaleResult.Canceled) {
                                            isCancelled.set(true)
                                            break
                                        }
                                        if (res is com.example.hdify.engine.UpscaleResult.Error) {
                                            throw RuntimeException("${res.code}: ${res.message}")
                                        }
                                    } else {
                                        canvasOut.drawBitmap(inBitmap, 0f, 0f, copyPaint)
                                    }

                                    if (isCancelled.get()) break
                                    val emitPtsUs = maxOf(currentPtsUs, lastEmittedPtsUs + 1000L)
                                    encoderSurface.drawBitmap(outBitmap, emitPtsUs * 1000L)
                                    lastEmittedPtsUs = emitPtsUs
                                    lastPtsUs = currentPtsUs
                                    processedFrames++

                                    Canvas(prevBitmap!!).drawBitmap(inBitmap, 0f, 0f, copyPaint)

                                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                                    val currentFps = processedFrames / elapsedSec
                                    val percent = if (targetTotalFrames > 0) ((processedFrames * 100) / targetTotalFrames).coerceIn(0, 99) else 50
                                    val remainingFrames = (targetTotalFrames - processedFrames).coerceAtLeast(0)
                                    val etaSec = if (currentFps > 0) (remainingFrames / currentFps).toLong() else 0L
                                    onProgress(processedFrames, targetTotalFrames, percent, currentFps, etaSec)
                                }
                            }

                            val isEosFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            if (isEosFrame && !isDecoderEos) {
                                isDecoderEos = true
                                try {
                                    encoder.signalEndOfInputStream()
                                } catch (e: Exception) {
                                    HdLogger.w(TAG, "Failed to signalEndOfInputStream: ${e.message}")
                                }
                            }
                        }
                        else -> {
                            if (isExtractorEos && !isDecoderEos) {
                                decoderDrainTries++
                                if (decoderDrainTries >= 20) {
                                    HdLogger.i(TAG, "Decoder drained after extractor EOS ($processedFrames frames), signaling encoder EOS")
                                    isDecoderEos = true
                                    try {
                                        encoder.signalEndOfInputStream()
                                    } catch (e: Exception) {
                                        HdLogger.w(TAG, "Failed to signalEndOfInputStream: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }

                // Drain encoder into muxer
                var encIndex = encoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
                    muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    isMuxerStarted = true
                }

                if (encIndex < 0 && isDecoderEos) {
                    eosDrainTries++
                    if (eosDrainTries >= 20) { // 20 * 10ms = 200ms without new buffers after EOS
                        HdLogger.i(TAG, "Encoder drain timeout reached after EOS, finalizing stream")
                        isEncoderEos = true
                    }
                } else if (encIndex >= 0) {
                    eosDrainTries = 0
                }

                while (encIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(encIndex)
                    if (encodedBuffer != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0) {
                            if (!isMuxerStarted) {
                                muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                isMuxerStarted = true
                            }
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encodedBuffer, bufferInfo)
                        }
                    }

                    encoder.releaseOutputBuffer(encIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEncoderEos = true
                        break
                    }
                    encIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                }
            }

            if (isCancelled.get()) {
                HdLogger.w(TAG, "Video pipeline cancelled by user")
                return false
            }

            // 4. Copy Audio Passthrough (FR-VID-05)
            if (audioExtractor != null && muxerAudioTrack >= 0 && isMuxerStarted && !isCancelled.get()) {
                try {
                    HdLogger.i(TAG, "Starting audio passthrough copy...")
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioSamplesCopied = 0

                    while (!isCancelled.get()) {
                        audioBuffer.clear()
                        val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                        if (sampleSize < 0) {
                            HdLogger.i(TAG, "Audio EOF reached after $audioSamplesCopied samples")
                            break
                        }

                        val sampleTime = audioExtractor.sampleTime
                        if (isPreview3s && sampleTime >= 3_000_000L) {
                            HdLogger.i(TAG, "Audio preview limit reached at ${sampleTime}us ($audioSamplesCopied samples)")
                            break
                        }

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
                    HdLogger.i(TAG, "Audio passthrough finished successfully: $audioSamplesCopied samples copied")
                } catch (t: Throwable) {
                    HdLogger.e(TAG, "Exception during audio passthrough copy: ${t.message}", t)
                }
            }

            // 5. Explicitly stop decoder, encoder and flush muxer to write MP4 header
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            decoder = null

            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            encoder = null

            if (isMuxerStarted) {
                try {
                    muxer?.stop()
                    HdLogger.i(TAG, "Video muxer stopped successfully. File size: ${outputFile.length()} bytes")
                } catch (muxEx: Throwable) {
                    HdLogger.e(TAG, "Error stopping video muxer: ${muxEx.message}", muxEx)
                }
                try { muxer?.release() } catch (_: Throwable) {}
                muxer = null
                isMuxerStarted = false
            }

            if (!outputFile.exists() || outputFile.length() < 1024) {
                HdLogger.e(TAG, "Output video file is missing or invalid (${outputFile.length()} bytes)")
                return false
            }

            val totalDurationSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val finalFps = processedFrames / totalDurationSec
            onProgress(processedFrames, processedFrames, 100, finalFps, 0L)

            HdLogger.i(TAG, "Video processing completed in ${(System.currentTimeMillis() - startTime)}ms ($processedFrames frames)")
            return true

        } finally {
            try { decoder?.stop() } catch (t: Throwable) {}
            try { decoder?.release() } catch (t: Throwable) {}
            try { encoder?.stop() } catch (t: Throwable) {}
            try { encoder?.release() } catch (t: Throwable) {}
            try { if (isMuxerStarted) muxer?.stop() } catch (t: Throwable) {}
            try { muxer?.release() } catch (t: Throwable) {}
            try { decoderSurface?.release() } catch (t: Throwable) {}
            try { encoderSurface?.release() } catch (t: Throwable) {}
            try { videoExtractor?.release() } catch (t: Throwable) {}
            try { audioExtractor?.release() } catch (t: Throwable) {}
            try { if (heapInBitmap?.isRecycled == false) heapInBitmap?.recycle() } catch (t: Throwable) {}
            try { if (heapPrevBitmap?.isRecycled == false) heapPrevBitmap?.recycle() } catch (t: Throwable) {}
            try { if (heapInterpBitmap?.isRecycled == false) heapInterpBitmap?.recycle() } catch (t: Throwable) {}
            try { if (heapOutBitmap?.isRecycled == false) heapOutBitmap?.recycle() } catch (t: Throwable) {}

            if (isCancelled.get() && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    companion object {
        private const val TAG = "VideoPipeline"
    }
}
