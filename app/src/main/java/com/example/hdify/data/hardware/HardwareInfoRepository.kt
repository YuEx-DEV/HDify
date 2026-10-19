package com.example.hdify.data.hardware

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.example.hdify.domain.model.CodecDetail
import com.example.hdify.domain.model.GpuInfo
import com.example.hdify.domain.model.HardwareCapabilities
import com.example.hdify.engine.NativeBridge
import com.example.hdify.logging.HdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareInfoRepository @Inject constructor() {

    @Volatile
    private var cachedCapabilities: HardwareCapabilities? = null
    private val cacheMutex = Mutex()

    suspend fun getHardwareCapabilities(forceRefresh: Boolean = false): HardwareCapabilities = withContext(Dispatchers.Default) {
        if (!forceRefresh) {
            cachedCapabilities?.let { return@withContext it }
        }
        cacheMutex.withLock {
            if (!forceRefresh) {
                cachedCapabilities?.let { return@withLock it }
            }
            val gpuInfo = queryGpuInfo()
            val decoders = mutableListOf<CodecDetail>()
            val encoders = mutableListOf<CodecDetail>()

        var avcDec = false
        var avcEnc = false
        var hevcDec = false
        var hevcEnc = false

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                // Focus on hardware-accelerated video codecs
                if (!info.isHardwareAccelerated) continue

                val supportedTypes = info.supportedTypes
                for (mime in supportedTypes) {
                    if (mime.equals("video/avc", ignoreCase = true) || mime.equals("video/hevc", ignoreCase = true)) {
                        try {
                            val caps = info.getCapabilitiesForType(mime)
                            val videoCaps = caps.videoCapabilities
                            val maxWidth = videoCaps?.supportedWidths?.upper ?: 0
                            val maxHeight = videoCaps?.supportedHeights?.upper ?: 0
                            val maxFps = videoCaps?.supportedFrameRates?.upper?.toDouble() ?: 0.0

                            val detail = CodecDetail(
                                name = info.name,
                                mimeType = mime,
                                isEncoder = info.isEncoder,
                                isHardwareAccelerated = true,
                                maxSupportedWidth = maxWidth,
                                maxSupportedHeight = maxHeight,
                                maxSupportedFps = maxFps
                            )

                            if (info.isEncoder) {
                                encoders.add(detail)
                                if (mime.equals("video/avc", ignoreCase = true)) avcEnc = true
                                if (mime.equals("video/hevc", ignoreCase = true)) hevcEnc = true
                            } else {
                                decoders.add(detail)
                                if (mime.equals("video/avc", ignoreCase = true)) avcDec = true
                                if (mime.equals("video/hevc", ignoreCase = true)) hevcDec = true
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (t: Throwable) {
            HdLogger.e("HardwareRepo", null, "HD-HW-102", "Failed to query MediaCodec capabilities", t)
        }

            val caps = HardwareCapabilities(
                gpuInfo = gpuInfo,
                hardwareDecoders = decoders,
                hardwareEncoders = encoders,
                avcDecoderAvailable = avcDec,
                avcEncoderAvailable = avcEnc,
                hevcDecoderAvailable = hevcDec,
                hevcEncoderAvailable = hevcEnc
            )
            cachedCapabilities = caps
            caps
        }
    }

    fun queryGpuInfo(): GpuInfo {
        return if (NativeBridge.isNativeLoaded()) {
            try {
                NativeBridge.getGpuInfo()
            } catch (t: Throwable) {
                HdLogger.e("HardwareRepo", null, "HD-HW-102", "JNI call getGpuInfo failed", t)
                createFallbackGpuInfo("JNI error: ${t.message}")
            }
        } else {
            HdLogger.e("HardwareRepo", null, "HD-HW-101", "Native library hdify_native not loaded")
            createFallbackGpuInfo("Native library failed to load")
        }
    }

    private fun createFallbackGpuInfo(error: String): GpuInfo {
        return GpuInfo(
            vulkanAvailable = false,
            gpuCount = 0,
            errorMessage = error,
            deviceName = "Tidak Diketahui",
            deviceType = "N/A",
            apiVersion = "0.0.0",
            driverVersion = "0.0.0",
            memoryHeapBytes = 0L,
            supportFp16Storage = false,
            supportFp16Arithmetic = false,
            supportFp16Packed = false
        )
    }
}
