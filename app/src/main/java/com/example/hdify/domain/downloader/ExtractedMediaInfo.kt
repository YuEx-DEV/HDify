package com.example.hdify.domain.downloader

data class ExtractedMediaInfo(
    val platform: MediaPlatform,
    val originalUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val options: List<MediaDownloadOption>
)
