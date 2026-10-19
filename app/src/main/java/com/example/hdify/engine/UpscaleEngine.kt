package com.example.hdify.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineInfo

sealed class UpscaleResult {
    data class Success(val durationMs: Long) : UpscaleResult()
    data class Canceled(val durationMs: Long) : UpscaleResult()
    data class Error(val code: String, val message: String) : UpscaleResult()
}

class EngineInitException(val code: String, message: String) : Exception("[$code] $message")

interface UpscaleEngine {
    @Throws(EngineInitException::class)
    fun init(assetManager: AssetManager, config: EngineConfig): EngineInfo
    fun upscale(inBitmap: Bitmap, outBitmap: Bitmap, callback: ProgressCallback?): UpscaleResult
    fun cancel()
    fun release()
}
