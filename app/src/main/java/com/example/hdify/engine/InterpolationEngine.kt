package com.example.hdify.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.hdify.domain.model.EngineInfo

sealed class InterpolationResult {
    data class Success(val durationMs: Long) : InterpolationResult()
    data class SceneCut(val durationMs: Long) : InterpolationResult()
    data class Canceled(val durationMs: Long) : InterpolationResult()
    data class Error(val code: String, val message: String) : InterpolationResult()
}

interface InterpolationEngine {
    @Throws(EngineInitException::class)
    fun init(
        assetManager: AssetManager,
        modelDir: String = "models/rife/rife-v4.6",
        gpuIndex: Int = 0,
        useFp16: Boolean = true,
        uhdMode: Boolean = false,
        allowCpuFallback: Boolean = false
    ): EngineInfo

    fun interpolate(
        in0Bitmap: Bitmap,
        in1Bitmap: Bitmap,
        timestep: Float,
        outBitmap: Bitmap,
        checkSceneCut: Boolean = true,
        sceneCutThreshold: Float = 0.55f
    ): InterpolationResult

    fun cancel()
    fun release()
}
