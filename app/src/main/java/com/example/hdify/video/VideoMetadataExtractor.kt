package com.example.hdify.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.hdify.logging.HdLogger

object VideoMetadataExtractor {
    private const val TAG = "VideoMetaExtractor"

    fun extract(context: Context, videoUri: Uri): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(context, videoUri)
            extractor.setDataSource(context, videoUri, null)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val frameCount = frameCountStr?.toLongOrNull() ?: 0L
            val captureRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val captureRate = captureRateStr?.toFloatOrNull()

            var width = widthStr?.toIntOrNull() ?: 0
            var height = heightStr?.toIntOrNull() ?: 0
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val bitrate = bitrateStr?.toLongOrNull() ?: 5_000_000L
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // If video is rotated 90 or 270 degrees, effective display dimensions are swapped
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            // Find tracks via MediaExtractor
            var videoFps = 0.0f
            var actualVideoMime = mimeStr
            var hasAudioTrack = false
            var audioMime: String? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    actualVideoMime = mime
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        videoFps = try {
                            format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        } catch (_: Exception) {
                            try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0f }
                        }
                    } else if (format.containsKey("capture-rate")) {
                        videoFps = try { format.getFloat("capture-rate") } catch (_: Exception) { 0f }
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudioTrack = true
                    audioMime = mime
                }
            }

            // Fallback for FPS detection: captureRate -> frameCount / duration -> 30.0f
            if (videoFps <= 0f) {
                if (captureRate != null && captureRate > 0f) {
                    videoFps = captureRate
                } else if (frameCount > 0 && durationMs > 0) {
                    videoFps = ((frameCount * 1000.0) / durationMs.toDouble()).toFloat()
                } else {
                    videoFps = 30.0f
                }
            }

            if (width <= 0 || height <= 0) {
                HdLogger.w(TAG, "Invalid dimensions from video URI: ${width}x${height}")
                return null
            }

            val meta = VideoMetadata(
                width = width,
                height = height,
                durationUs = durationMs * 1000,
                fps = if (videoFps > 0f) videoFps else 30.0f,
                bitrate = bitrate,
                mimeType = actualVideoMime,
                rotationDegrees = rotation,
                hasAudio = hasAudioTrack,
                audioMimeType = audioMime,
                frameCount = frameCount
            )

            HdLogger.i(TAG, "Extracted video meta: ${meta.width}x${meta.height}, ${meta.fps} FPS, frames=${meta.totalFrames}, ${meta.getFormattedDuration()}, mime=${meta.mimeType}, audio=$hasAudioTrack")
            return meta

        } catch (e: Exception) {
            HdLogger.e(TAG, "HD-VID-401 Failed to extract video metadata: ${e.message}", e)
            return null
        } finally {
            try {
                retriever.release()
                extractor.release()
            } catch (t: Throwable) {
                // Ignore cleanup errors
            }
        }
    }
}
