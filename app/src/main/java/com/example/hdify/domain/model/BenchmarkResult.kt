package com.example.hdify.domain.model

data class BenchmarkResult(
    val passed: Boolean,
    val engineName: String,
    val scale: Int,
    val msPerTile: Long,
    val est1080pSec: Double,
    val est720pVideoSec: Double,
    val fps720p: Double,
    val errorMessage: String
)
