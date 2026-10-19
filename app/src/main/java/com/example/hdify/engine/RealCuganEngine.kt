package com.example.hdify.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineInfo
import com.example.hdify.logging.HdLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealCuganEngine @Inject constructor() : UpscaleEngine {
    private var nativeHandle: Long = 0L
    private var engineInfo: EngineInfo? = null

    override fun init(assetManager: AssetManager, config: EngineConfig): EngineInfo {
        if (!NativeBridge.isNativeLoaded()) {
            HdLogger.e(TAG, "HD-SYS-901 Native library libhdify_native.so failed to load")
            throw EngineInitException("HD-SYS-901", "Native library failed to load")
        }

        if (nativeHandle != 0L) {
            release()
        }

        nativeHandle = NativeBridge.createEngine()
        if (nativeHandle == 0L) {
            HdLogger.e(TAG, "HD-SYS-902 Failed to allocate native RealCUGAN context")
            throw EngineInitException("HD-SYS-902", "Failed to allocate native engine")
        }

        var info = NativeBridge.initEngine(
            handle = nativeHandle,
            assetManager = assetManager,
            engineType = config.engine.ordinal,
            modelDir = config.modelDirectory,
            scale = config.scale,
            denoise = config.denoise,
            tileSize = config.tileSize,
            useFp16 = config.useFp16,
            gpuIndex = config.gpuIndex,
            allowCpuFallback = config.allowCpuFallback
        )

        if (info == null && config.modelDirectory == "models-pro") {
            HdLogger.w(TAG, "HD-MDL-202 Model models-pro gagal dimuat (scale=${config.scale}), fallback Level 1: models-se...")
            val fallbackDenoise = if (config.denoise !in listOf(-1, 0, 1, 2, 3)) 0 else config.denoise
            info = NativeBridge.initEngine(
                handle = nativeHandle,
                assetManager = assetManager,
                engineType = config.engine.ordinal,
                modelDir = "models-se",
                scale = config.scale,
                denoise = fallbackDenoise,
                tileSize = config.tileSize,
                useFp16 = config.useFp16,
                gpuIndex = config.gpuIndex,
                allowCpuFallback = config.allowCpuFallback
            )
            if (info != null) {
                HdLogger.i(TAG, "Fallback ke models-se sukses! (scale=${config.scale}, denoise=$fallbackDenoise)")
            }
        }

        if (info == null) {
            HdLogger.w(TAG, "HD-MDL-202 Fallback Level 2: models-se no-denoise (scale=${config.scale})...")
            info = NativeBridge.initEngine(
                handle = nativeHandle,
                assetManager = assetManager,
                engineType = config.engine.ordinal,
                modelDir = "models-se",
                scale = config.scale,
                denoise = 0,
                tileSize = config.tileSize,
                useFp16 = config.useFp16,
                gpuIndex = config.gpuIndex,
                allowCpuFallback = config.allowCpuFallback
            )
            if (info != null) {
                HdLogger.i(TAG, "Fallback ke models-se no-denoise sukses! (scale=${config.scale})")
            }
        }

        if (info == null) {
            HdLogger.w(TAG, "HD-MDL-202 Fallback Level 3: models-se conservative (scale=${config.scale})...")
            info = NativeBridge.initEngine(
                handle = nativeHandle,
                assetManager = assetManager,
                engineType = config.engine.ordinal,
                modelDir = "models-se",
                scale = config.scale,
                denoise = -1,
                tileSize = config.tileSize,
                useFp16 = config.useFp16,
                gpuIndex = config.gpuIndex,
                allowCpuFallback = config.allowCpuFallback
            )
            if (info != null) {
                HdLogger.i(TAG, "Fallback ke models-se conservative sukses! (scale=${config.scale})")
            }
        }

        if (info == null && config.allowCpuFallback) {
            HdLogger.w(TAG, "HD-HW-101 Fallback Level 4: CPU mode dengan models-se no-denoise...")
            info = NativeBridge.initEngine(
                handle = nativeHandle,
                assetManager = assetManager,
                engineType = config.engine.ordinal,
                modelDir = "models-se",
                scale = config.scale,
                denoise = 0,
                tileSize = config.tileSize,
                useFp16 = false,
                gpuIndex = -1,
                allowCpuFallback = true
            )
            if (info != null) {
                HdLogger.i(TAG, "Fallback CPU mode sukses! (scale=${config.scale})")
            }
        }

        if (info == null) {
            val errCode = if (config.gpuIndex >= 0 && !config.allowCpuFallback) "HD-HW-101" else "HD-MDL-201"
            val errMsg = "Initialization of Real-CUGAN engine failed (model=${config.modelDirectory}, scale=${config.scale})"
            HdLogger.e(TAG, "$errCode $errMsg")
            release()
            throw EngineInitException(errCode, errMsg)
        }

        engineInfo = info
        HdLogger.i(TAG, "backend=${info.backend} device=${info.deviceName} fp16=${info.fp16Active} tile=${info.tileSize}")
        return info
    }

    override fun upscale(inBitmap: Bitmap, outBitmap: Bitmap, callback: ProgressCallback?): UpscaleResult {
        if (nativeHandle == 0L) {
            return UpscaleResult.Error("HD-SYS-903", "Engine is not initialized")
        }

        val startTime = System.currentTimeMillis()
        val ret = NativeBridge.upscaleBitmap(nativeHandle, inBitmap, outBitmap, callback)
        val duration = System.currentTimeMillis() - startTime

        return when (ret) {
            0 -> UpscaleResult.Success(duration)
            -2 -> {
                HdLogger.i(TAG, "Upscale canceled by user after ${duration}ms")
                UpscaleResult.Canceled(duration)
            }
            -301 -> UpscaleResult.Error("HD-IMG-301", "Failed to lock bitmap pixels")
            -302 -> UpscaleResult.Error("HD-IMG-302", "Invalid bitmap pixel format (must be ARGB_8888)")
            -303 -> UpscaleResult.Error("HD-IMG-303", "Output bitmap dimensions do not match scale factor")
            else -> {
                val code = "HD-IMG-304"
                val msg = "Inference execution failed with internal code $ret"
                HdLogger.e(TAG, "$code $msg")
                UpscaleResult.Error(code, msg)
            }
        }
    }

    override fun cancel() {
        if (nativeHandle != 0L) {
            NativeBridge.cancelEngine(nativeHandle)
        }
    }

    override fun release() {
        if (nativeHandle != 0L) {
            NativeBridge.releaseEngine(nativeHandle)
            nativeHandle = 0L
            engineInfo = null
        }
    }

    companion object {
        private const val TAG = "RealCuganEngine"
    }
}
