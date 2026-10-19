package com.example.hdify.domain.downloader

data class MediaDownloadOption(
    val id: String,
    val label: String,
    val format: DownloadFormat,
    val url: String,
    val quality: String? = null,
    val sizeBytes: Long? = null,
    val thumbnailUrl: String? = null
)
