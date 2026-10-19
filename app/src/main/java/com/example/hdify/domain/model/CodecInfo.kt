package com.example.hdify.domain.model

data class CodecDetail(
    val name: String,
    val mimeType: String,
    val isEncoder: Boolean,
    val isHardwareAccelerated: Boolean,
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int,
    val maxSupportedFps: Double
)

data class HardwareCapabilities(
    val gpuInfo: GpuInfo,
    val hardwareDecoders: List<CodecDetail>,
    val hardwareEncoders: List<CodecDetail>,
    val avcDecoderAvailable: Boolean,
    val avcEncoderAvailable: Boolean,
    val hevcDecoderAvailable: Boolean,
    val hevcEncoderAvailable: Boolean
)
