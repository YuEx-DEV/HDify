package com.example.hdify.video

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val fps: Float,
    val bitrate: Long,
    val mimeType: String,
    val rotationDegrees: Int,
    val hasAudio: Boolean,
    val audioMimeType: String? = null,
    val frameCount: Long = 0L
) {
    val durationMs: Long get() = durationUs / 1000
    val totalFrames: Long get() = if (frameCount > 0) frameCount else if (fps > 0) ((durationMs * fps) / 1000).toLong().coerceAtLeast(1) else 1L

    val displayWidth: Int get() = width
    val displayHeight: Int get() = height

    fun getFormattedDuration(): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    fun getFormattedBitrate(): String {
        return if (bitrate > 1_000_000) {
            "%.1f Mbps".format(bitrate / 1_000_000.0)
        } else if (bitrate > 1000) {
            "%d kbps".format(bitrate / 1000)
        } else {
            "$bitrate bps"
        }
    }
}
