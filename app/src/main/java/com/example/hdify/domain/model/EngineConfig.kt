package com.example.hdify.domain.model

enum class EngineType {
    REAL_CUGAN,
    REAL_ESRGAN
}

enum class ContentType {
    ANIME,
    PHOTO
}

enum class Preset {
    FAST,
    BALANCED,
    QUALITY
}

data class EngineConfig(
    val engine: EngineType = EngineType.REAL_CUGAN,
    val modelDirectory: String = "models-se",
    val scale: Int = 2,
    val denoise: Int = 0,
    val tileSize: Int = 0,
    val useFp16: Boolean = true,
    val gpuIndex: Int = 0,
    val allowCpuFallback: Boolean = false
)

data class EngineInfo(
    val backend: String,
    val deviceName: String,
    val fp16Active: Boolean,
    val tileSize: Int
)

data class SelfTestResult(
    val passed: Boolean,
    val fp16Passed: Boolean,
    val fp32FallbackUsed: Boolean,
    val deviceName: String,
    val message: String,
    val errorCode: String,
    val durationMs: Long
)
