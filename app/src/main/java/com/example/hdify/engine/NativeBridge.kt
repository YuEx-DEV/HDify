package com.example.hdify.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.hdify.domain.model.BenchmarkResult
import com.example.hdify.domain.model.EngineInfo
import com.example.hdify.domain.model.GpuInfo
import com.example.hdify.domain.model.SelfTestResult

fun interface ProgressCallback {
    fun onProgress(current: Int, total: Int)
}

object NativeBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("hdify_native")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isLoaded

    // Hardware & Diagnostics
    external fun getGpuInfo(): GpuInfo
    external fun initNativeLogger(logFilePath: String, verbose: Boolean)
    external fun logNative(level: Int, tag: String, context: String, message: String)
    external fun addBreadcrumb(message: String)
    external fun installCrashHandler(crashDir: String): Boolean
    external fun triggerNativeCrash()
    external fun runGpuSelfTest(assetManager: AssetManager, gpuIndex: Int): SelfTestResult
    external fun runBenchmark(assetManager: AssetManager, engineType: Int, scale: Int, gpuIndex: Int): BenchmarkResult

    // Engine Lifecycle & Inference
    external fun createEngine(): Long
    external fun initEngine(
        handle: Long,
        assetManager: AssetManager,
        engineType: Int,
        modelDir: String,
        scale: Int,
        denoise: Int,
        tileSize: Int,
        useFp16: Boolean,
        gpuIndex: Int,
        allowCpuFallback: Boolean
    ): EngineInfo?
    external fun upscaleBitmap(
        handle: Long,
        inBitmap: Bitmap,
        outBitmap: Bitmap,
        callback: ProgressCallback?
    ): Int
    external fun cancelEngine(handle: Long)
    external fun releaseEngine(handle: Long)

    // RIFE Frame Interpolation (Fase 4)
    external fun createRifeEngine(): Long
    external fun initRifeEngine(
        handle: Long,
        assetManager: AssetManager,
        modelDir: String,
        gpuIndex: Int,
        useFp16: Boolean,
        uhdMode: Boolean,
        allowCpuFallback: Boolean
    ): EngineInfo?
    external fun interpolateFrames(
        handle: Long,
        in0Bitmap: Bitmap,
        in1Bitmap: Bitmap,
        timestep: Float,
        outBitmap: Bitmap
    ): Int
    external fun detectSceneCut(
        in0Bitmap: Bitmap,
        in1Bitmap: Bitmap,
        threshold: Float
    ): Boolean
    external fun cancelRifeEngine(handle: Long)
    external fun releaseRifeEngine(handle: Long)
}
