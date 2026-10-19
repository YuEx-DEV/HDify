package com.example.hdify.video.compressor

import android.media.MediaFormat
import com.example.hdify.video.VideoMetadata
import java.util.Locale

data class VideoCompressorConfig(
    val preset: CompressPreset,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetFps: Float,
    val bitrateBps: Long,
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val iFrameIntervalSeconds: Int = 1,
    val keepOriginalFps: Boolean = true,
    val forceHdr10Plus: Boolean = false
) {
    companion object {
        fun fromPreset(
            preset: CompressPreset,
            meta: VideoMetadata,
            originalFileSize: Long = 0L,
            customBitrateMbps: Float = 10f,
            customMaxDimension: Int = 9999,
            customMime: String = MediaFormat.MIMETYPE_VIDEO_AVC,
            keepOriginalFps: Boolean = true,
            forceHdr10Plus: Boolean = false
        ): VideoCompressorConfig {
            val maxDim = if (preset == CompressPreset.CUSTOM) customMaxDimension else preset.maxDimension
            val (targetW, targetH) = calculateTargetDimensions(
                inWidth = meta.displayWidth,
                inHeight = meta.displayHeight,
                maxDimension = maxDim
            )

            val effectiveOriginalBitrate = if (meta.bitrate > 0L) {
                meta.bitrate
            } else if (originalFileSize > 0L && meta.durationUs > 0L) {
                ((originalFileSize * 8.0) / (meta.durationUs / 1_000_000.0)).toLong()
            } else {
                0L
            }

            val targetBitrate = if (preset == CompressPreset.CUSTOM) {
                (customBitrateMbps * 1_000_000L).toLong()
            } else {
                preset.calculateTargetBitrate(effectiveOriginalBitrate, targetW, targetH, meta.fps)
            }

            // HDR 10+ strictly requires HEVC (H.265) encoding
            val targetMime = if (forceHdr10Plus) {
                MediaFormat.MIMETYPE_VIDEO_HEVC
            } else if (preset == CompressPreset.CUSTOM) {
                customMime
            } else {
                preset.recommendedCodec
            }

            val targetFps = if (keepOriginalFps && meta.fps > 0f) {
                meta.fps
            } else {
                minOf(meta.fps, 60.0f).coerceAtLeast(24.0f)
            }

            return VideoCompressorConfig(
                preset = preset,
                targetWidth = targetW,
                targetHeight = targetH,
                targetFps = targetFps,
                bitrateBps = targetBitrate,
                mimeType = targetMime,
                iFrameIntervalSeconds = preset.iFrameIntervalSec,
                keepOriginalFps = keepOriginalFps,
                forceHdr10Plus = forceHdr10Plus
            )
        }

        fun calculateTargetDimensions(inWidth: Int, inHeight: Int, maxDimension: Int): Pair<Int, Int> {
            if (maxDimension >= 9999 || inWidth <= 0 || inHeight <= 0) {
                return Pair((inWidth / 2) * 2, (inHeight / 2) * 2)
            }

            val currentShort = minOf(inWidth, inHeight)
            val currentLong = maxOf(inWidth, inHeight)

            val maxShort = if (maxDimension <= 720) 720 else 1080
            val maxLong = if (maxDimension <= 720) 1280 else 1920

            if (currentShort <= maxShort && currentLong <= maxLong) {
                return Pair((inWidth / 2) * 2, (inHeight / 2) * 2)
            }

            val scaleShort = maxShort.toDouble() / currentShort.toDouble()
            val scaleLong = maxLong.toDouble() / currentLong.toDouble()
            val scale = minOf(scaleShort, scaleLong)

            val targetW = ((inWidth * scale).toInt() / 2) * 2
            val targetH = ((inHeight * scale).toInt() / 2) * 2

            return Pair(targetW.coerceAtLeast(16), targetH.coerceAtLeast(16))
        }

        fun estimateOutputSizeBytes(bitrateBps: Long, durationUs: Long): Long {
            val durationSec = durationUs / 1_000_000.0
            val audioBitrateBps = 192_000L
            val totalBitrate = bitrateBps + audioBitrateBps
            return ((totalBitrate * durationSec) / 8.0).toLong().coerceAtLeast(1024L)
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
