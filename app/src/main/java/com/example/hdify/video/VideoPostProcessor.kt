package com.example.hdify.video

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdify.logging.HdLogger
import com.example.hdify.video.gl.VideoTransformSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object VideoPostProcessor {
    private const val TAG = "VideoPostProcessor"

    suspend fun processAndSave(
        context: Context,
        tempFile: File,
        rotationDegrees: Int,
        isMirrored: Boolean,
        inputWidth: Int,
        inputHeight: Int,
        isPreview: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        // If no rotation and no mirror, directly save to MediaStore
        if (rotationDegrees == 0 && !isMirrored) {
            HdLogger.i(TAG, "No transform needed. Saving directly to MediaStore: ${inputWidth}x${inputHeight}")
            val finalUri = saveDirectToMediaStore(context, tempFile, inputWidth, inputHeight, isPreview)
            try { tempFile.delete() } catch (_: Throwable) {}
            return@withContext finalUri
        }

        val swapDim = rotationDegrees == 90 || rotationDegrees == 270
        val outWidth = ((if (swapDim) inputHeight else inputWidth) / 2) * 2
        val outHeight = ((if (swapDim) inputWidth else inputHeight) / 2) * 2

        HdLogger.i(TAG, "Starting GPU video transform: rot=$rotationDegrees, mirror=$isMirrored, in=${inputWidth}x${inputHeight} -> out=${outWidth}x${outHeight}")

        val transformedTempFile = File(context.cacheDir, "hdify_trans_${System.currentTimeMillis()}.mp4")

        try {
            transformVideo(
                sourceFile = tempFile,
                destFile = transformedTempFile,
                rotationDegrees = rotationDegrees,
                isMirrored = isMirrored,
                inWidth = inputWidth,
                inHeight = inputHeight,
                outWidth = outWidth,
                outHeight = outHeight,
                onProgress = onProgress
            )

            val finalUri = saveDirectToMediaStore(context, transformedTempFile, outWidth, outHeight, isPreview)
            try { transformedTempFile.delete() } catch (_: Throwable) {}
            try { tempFile.delete() } catch (_: Throwable) {}
            finalUri
        } catch (e: Exception) {
            HdLogger.e(TAG, "GPU transform failed: ${e.message}", e)
            try { transformedTempFile.delete() } catch (_: Throwable) {}
            throw e
        }
    }

    private fun transformVideo(
        sourceFile: File,
        destFile: File,
        rotationDegrees: Int,
        isMirrored: Boolean,
        inWidth: Int,
        inHeight: Int,
        outWidth: Int,
        outHeight: Int,
        onProgress: (Int) -> Unit
    ) {
        val videoExtractor = MediaExtractor().apply { setDataSource(sourceFile.absolutePath) }
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var transformSurface: VideoTransformSurface? = null
        var isMuxerStarted = false

        try {
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            if (videoTrackIndex < 0) throw IllegalStateException("No video track found in temp file")
            videoExtractor.selectTrack(videoTrackIndex)
            val inputVideoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

            val actualInWidth = if (inputVideoFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
            } else inWidth

            val actualInHeight = if (inputVideoFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            } else inHeight

            val swapDim = rotationDegrees == 90 || rotationDegrees == 270
            val actualOutWidth = ((if (swapDim) actualInHeight else actualInWidth) / 2) * 2
            val actualOutHeight = ((if (swapDim) actualInWidth else actualInHeight) / 2) * 2

            val audioTrackIndex = selectTrack(videoExtractor, "audio/")
            if (audioTrackIndex >= 0) {
                audioExtractor = MediaExtractor().apply { setDataSource(sourceFile.absolutePath) }
                audioExtractor.selectTrack(audioTrackIndex)
            }

            // Encoder & Decoder configuration
            val encoderMime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            val fps = try {
                if (inputVideoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    inputVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                } else 30.0f
            } catch (_: Exception) {
                try {
                    inputVideoFormat.getFloat(MediaFormat.KEY_FRAME_RATE)
                } catch (_: Exception) {
                    30.0f
                }
            }
            val bitrate = if (inputVideoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                inputVideoFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            } else 12_000_000

            // 1. Setup Encoder
            val outputVideoFormat = MediaFormat.createVideoFormat(encoderMime, actualOutWidth, actualOutHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setFloat(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(encoderMime).apply {
                configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val encoderInputSurface = encoder.createInputSurface()

            // 2. Setup GPU Transform Surface
            transformSurface = VideoTransformSurface(
                encoderInputSurface = encoderInputSurface,
                outWidth = actualOutWidth,
                outHeight = actualOutHeight,
                rotationDegrees = rotationDegrees,
                isMirrored = isMirrored
            )

            // 3. Setup Decoder
            val decoderMime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(decoderMime).apply {
                configure(inputVideoFormat, transformSurface.decoderSurface, null, 0)
                start()
            }
            encoder.start()

            // 4. Setup Muxer
            muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1


            if (audioExtractor != null && audioTrackIndex >= 0) {
                val inputAudioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                try {
                    muxerAudioTrack = muxer.addTrack(inputAudioFormat)
                    HdLogger.i(TAG, "Audio track added directly to transform muxer: index=$muxerAudioTrack")
                } catch (t: Throwable) {
                    HdLogger.w(TAG, "Direct addTrack for audio failed in transform: ${t.message}. Trying sanitized audio format...")
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
                        HdLogger.i(TAG, "Sanitized audio track added to transform muxer: index=$muxerAudioTrack")
                    } catch (t2: Throwable) {
                        HdLogger.e(TAG, "Sanitized addTrack in transform also failed: ${t2.message}. Continuing with video-only.", t2)
                        muxerAudioTrack = -1
                    }
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val kTimeoutUs = 5_000L
            var isExtractorEos = false
            var isDecoderEos = false
            var isEncoderEos = false
            var decoderDrainTries = 0
            var eosDrainTries = 0

            var framesProcessed = 0
            val totalDurationUs = if (inputVideoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputVideoFormat.getLong(MediaFormat.KEY_DURATION)
            } else 1_000_000L

            while (!isEncoderEos) {
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
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, videoExtractor.sampleTime, 0)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                // Drain decoder
                if (!isDecoderEos) {
                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                    when {
                        outIndex >= 0 -> {
                            decoderDrainTries = 0
                            val doRender = bufferInfo.size > 0
                            decoder.releaseOutputBuffer(outIndex, doRender)

                            if (doRender) {
                                transformSurface.drawFrame(bufferInfo.presentationTimeUs * 1000L)
                                framesProcessed++

                                val percent = if (totalDurationUs > 0) {
                                    ((bufferInfo.presentationTimeUs * 100) / totalDurationUs).toInt().coerceIn(0, 99)
                                } else 50
                                onProgress(percent)
                            }

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isDecoderEos = true
                                try {
                                    encoder.signalEndOfInputStream()
                                } catch (_: Throwable) {}
                            }
                        }
                        else -> {
                            if (isExtractorEos && !isDecoderEos) {
                                decoderDrainTries++
                                if (decoderDrainTries >= 20) {
                                    isDecoderEos = true
                                    try {
                                        encoder.signalEndOfInputStream()
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }

                // Drain encoder
                var encIndex = encoder.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
                    muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    isMuxerStarted = true
                }

                if (encIndex < 0 && isDecoderEos) {
                    eosDrainTries++
                    if (eosDrainTries >= 25) { // 25 * 5ms = 125ms timeout after EOS
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

            // Audio passthrough copy
            if (muxerAudioTrack >= 0 && audioExtractor != null && isMuxerStarted) {
                try {
                    HdLogger.i(TAG, "Starting audio passthrough copy in VideoPostProcessor...")
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioSamplesCopied = 0

                    while (true) {
                        audioBuffer.clear()
                        val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                        if (sampleSize < 0) {
                            HdLogger.i(TAG, "Audio EOF reached after $audioSamplesCopied samples")
                            break
                        }

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
                    HdLogger.i(TAG, "Audio passthrough finished successfully: $audioSamplesCopied samples copied")
                } catch (t: Throwable) {
                    HdLogger.e(TAG, "Exception during audio passthrough copy in VideoPostProcessor: ${t.message}", t)
                }
            }

            onProgress(100)
            HdLogger.i(TAG, "GPU video transform finished successfully: $framesProcessed frames")

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

    fun saveDirectToMediaStore(context: Context, file: File, width: Int, height: Int, isPreview: Boolean): Uri {
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
                FileInputStream(file).use { inStream ->
                    inStream.copyTo(out)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HDify")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val destFile = File(moviesDir, filename)
            file.copyTo(destFile, overwrite = true)
            return Uri.fromFile(destFile)
        }
    }
}
