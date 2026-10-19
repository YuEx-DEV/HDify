package com.example.hdify.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.example.hdify.logging.HdLogger

object VideoCapabilitiesHelper {
    private const val TAG = "VideoCapabilities"

    private val encoderCache = java.util.concurrent.ConcurrentHashMap<String, EncoderCapability?>()
    private val sizeSupportCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    data class EncoderCapability(
        val codecName: String,
        val isHardware: Boolean,
        val maxWidth: Int,
        val maxHeight: Int,
        val maxBitrate: Int,
        val maxFrameRate: Int = 120
    )

    fun hasHardwareHevcEncoder(): Boolean {
        return findEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC, preferHardware = true)?.isHardware == true
    }

    fun findEncoder(mimeType: String, preferHardware: Boolean = true): EncoderCapability? {
        val cacheKey = "$mimeType:$preferHardware"
        if (encoderCache.containsKey(cacheKey)) {
            return encoderCache[cacheKey]
        }

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        var fallbackInfo: EncoderCapability? = null

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue

            val supportedTypes = info.supportedTypes
            if (supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                val caps = try {
                    info.getCapabilitiesForType(mimeType)
                } catch (e: Exception) {
                    continue
                }

                val videoCaps = caps.videoCapabilities
                val maxWidth = videoCaps?.supportedWidths?.upper ?: 1920
                val maxHeight = videoCaps?.supportedHeights?.upper ?: 1080
                val maxBitrate = videoCaps?.bitrateRange?.upper ?: 20_000_000
                val maxFrameRate = videoCaps?.supportedFrameRates?.upper?.toInt() ?: 120

                val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                }

                val enc = EncoderCapability(
                    codecName = info.name,
                    isHardware = isHw,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    maxBitrate = maxBitrate,
                    maxFrameRate = maxFrameRate
                )

                if (isHw && preferHardware) {
                    HdLogger.i(TAG, "Selected hardware encoder: ${enc.codecName} for $mimeType (Max: ${maxWidth}x$maxHeight)")
                    encoderCache[cacheKey] = enc
                    return enc
                }

                if (fallbackInfo == null) {
                    fallbackInfo = enc
                }
            }
        }

        encoderCache[cacheKey] = fallbackInfo
        return fallbackInfo
    }

    fun isSizeSupported(mimeType: String, width: Int, height: Int): Boolean {
        val cacheKey = "$mimeType:${width}x$height"
        sizeSupportCache[cacheKey]?.let { return it }

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                val caps = try {
                    info.getCapabilitiesForType(mimeType).videoCapabilities
                } catch (e: Exception) {
                    continue
                }

                if (caps != null) {
                    val supported = caps.isSizeSupported(width, height)
                    if (supported) {
                        sizeSupportCache[cacheKey] = true
                        return true
                    }
                }
            }
        }
        sizeSupportCache[cacheKey] = false
        return false
    }

    /**
     * Calculates recommended adaptive bitrate in bps based on target resolution and codec (FR-VID-07).
     * Tuned for high-fidelity AI upscaled and interpolated video to avoid macroblocking ("bitrate pecah").
     * @param qualityPercent 10 to 100
     */
    fun calculateAdaptiveBitrate(
        width: Int,
        height: Int,
        fps: Float,
        isHevc: Boolean,
        qualityPercent: Int = 90
    ): Int {
        val pixelCount = width.toLong() * height.toLong()
        // High-fidelity bits-per-pixel heuristic for crisp AI edges and high framerates
        val baseBpp = if (isHevc) 0.18 else 0.28
        val standardBitrate = (pixelCount * fps.toDouble() * baseBpp).toLong()

        val adjustedBitrate = ((standardBitrate * qualityPercent.toLong()) / 100L).coerceIn(6_000_000L, 80_000_000L).toInt()
        return adjustedBitrate
    }
}
