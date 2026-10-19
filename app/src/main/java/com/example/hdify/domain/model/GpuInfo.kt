package com.example.hdify.domain.model

data class GpuInfo(
    val vulkanAvailable: Boolean,
    val gpuCount: Int,
    val errorMessage: String,
    val deviceName: String,
    val deviceType: String,
    val apiVersion: String,
    val driverVersion: String,
    val memoryHeapBytes: Long,
    val supportFp16Storage: Boolean,
    val supportFp16Arithmetic: Boolean,
    val supportFp16Packed: Boolean
)
