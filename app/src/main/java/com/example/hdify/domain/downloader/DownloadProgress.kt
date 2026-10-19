package com.example.hdify.domain.downloader

import android.net.Uri

sealed class DownloadProgress {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int
    ) : DownloadProgress()

    data class Success(
        val fileUri: Uri,
        val filePath: String,
        val format: DownloadFormat
    ) : DownloadProgress()

    data class Error(
        val message: String
    ) : DownloadProgress()
}
