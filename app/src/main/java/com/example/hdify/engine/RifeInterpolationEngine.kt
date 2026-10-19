package com.example.hdify.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.hdify.domain.model.EngineInfo
import com.example.hdify.logging.BreadcrumbManager
import com.example.hdify.logging.HdLogger

class RifeInterpolationEngine : InterpolationEngine {

    companion object {
        private const val TAG = "RifeEngine"
    }

    private var handle: Long = 0L
    private var engineInfo: EngineInfo? = null
    private val copyPaint = Paint()

    @Synchronized
    override fun init(
        assetManager: AssetManager,
        modelDir: String,
        gpuIndex: Int,
        useFp16: Boolean,
        uhdMode: Boolean,
        allowCpuFallback: Boolean
    ): EngineInfo {
        if (!NativeBridge.isNativeLoaded()) {
            throw EngineInitException("HD-SYS-901", "Native library hdify_native gagal dimuat")
        }

        release()

        val newHandle = NativeBridge.createRifeEngine()
        if (newHandle == 0L) {
            throw EngineInitException("HD-SYS-901", "Gagal mengalokasikan konteks engine RIFE native")
        }

        BreadcrumbManager.add("Init RIFE: model=$modelDir, gpu=$gpuIndex, fp16=$useFp16, uhd=$uhdMode")

        var info = NativeBridge.initRifeEngine(
            newHandle,
            assetManager,
            modelDir,
            gpuIndex,
            useFp16,
            uhdMode,
            allowCpuFallback
        )

        // Fallback Level 1: Jika mode UHD gagal karena memori GPU tidak cukup, coba tanpa UHD
        if (info == null && uhdMode) {
            HdLogger.w(TAG, "Inisialisasi RIFE dengan mode UHD gagal, mencoba ulang tanpa mode UHD...")
            info = NativeBridge.initRifeEngine(
                newHandle,
                assetManager,
                modelDir,
                gpuIndex,
                useFp16,
                false,
                allowCpuFallback
            )
        }

        // Fallback Level 2: Jika GPU gagal dan CPU fallback diizinkan, fallback ke CPU
        if (info == null && allowCpuFallback && gpuIndex >= 0) {
            HdLogger.w(TAG, "Inisialisasi RIFE pada GPU gagal, fallback ke mode CPU...")
            info = NativeBridge.initRifeEngine(
                newHandle,
                assetManager,
                "models/rife/rife-v4.6",
                -1,
                false,
                false,
                true
            )
        }

        if (info == null) {
            NativeBridge.releaseRifeEngine(newHandle)
            throw EngineInitException(
                "HD-MDL-201",
                "Gagal memuat model RIFE dari $modelDir (Vulkan error atau memori tidak cukup)"
            )
        }

        handle = newHandle
        engineInfo = info
        HdLogger.i(TAG, "RIFE engine initialized: backend=${info.backend}, device=${info.deviceName}, fp16=${info.fp16Active}")
        return info
    }

    override fun interpolate(
        in0Bitmap: Bitmap,
        in1Bitmap: Bitmap,
        timestep: Float,
        outBitmap: Bitmap,
        checkSceneCut: Boolean,
        sceneCutThreshold: Float
    ): InterpolationResult {
        if (handle == 0L) {
            return InterpolationResult.Error("HD-SYS-901", "RIFE engine belum diinisialisasi")
        }

        val startTime = System.currentTimeMillis()

        // FR-INT-04: Scene cut detection
        if (checkSceneCut) {
            val isCut = NativeBridge.detectSceneCut(in0Bitmap, in1Bitmap, sceneCutThreshold)
            if (isCut) {
                // Duplicate previous frame to prevent morphing artifacts
                val canvas = Canvas(outBitmap)
                canvas.drawBitmap(in0Bitmap, 0f, 0f, copyPaint)
                val duration = System.currentTimeMillis() - startTime
                HdLogger.d(TAG, "Scene cut detected (threshold=$sceneCutThreshold). Frame duplicated, RIFE skipped.")
                return InterpolationResult.SceneCut(duration)
            }
        }

        val ret = NativeBridge.interpolateFrames(handle, in0Bitmap, in1Bitmap, timestep, outBitmap)
        val duration = System.currentTimeMillis() - startTime

        return when (ret) {
            0 -> InterpolationResult.Success(duration)
            -2 -> InterpolationResult.Canceled(duration)
            -301 -> InterpolationResult.Error("HD-IMG-301", "Gagal mengunci buffer bitmap untuk interpolasi")
            -302 -> InterpolationResult.Error("HD-IMG-302", "Format piksel bitmap tidak didukung (harus RGBA_8888)")
            -303 -> InterpolationResult.Error("HD-IMG-303", "Dimensi bitmap in0, in1, dan out tidak sesuai")
            else -> InterpolationResult.Error("HD-INT-402", "Inferensi RIFE gagal dengan kode $ret")
        }
    }

    override fun cancel() {
        if (handle != 0L) {
            NativeBridge.cancelRifeEngine(handle)
        }
    }

    @Synchronized
    override fun release() {
        if (handle != 0L) {
            NativeBridge.releaseRifeEngine(handle)
            handle = 0L
            engineInfo = null
            HdLogger.d(TAG, "RIFE engine released")
        }
    }
}
