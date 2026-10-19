package com.example.hdify.ui.downloader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.R
import com.example.hdify.data.downloader.MediaDownloaderRepository
import com.example.hdify.domain.downloader.DownloadFormat
import com.example.hdify.domain.downloader.DownloadProgress
import com.example.hdify.domain.downloader.ExtractedMediaInfo
import com.example.hdify.domain.downloader.MediaDownloadOption
import com.example.hdify.domain.downloader.MediaPlatform
import com.example.hdify.service.UpscaleForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DownloaderUiState {
    object Idle : DownloaderUiState()
    object Analyzing : DownloaderUiState()
    data class MediaReady(val info: ExtractedMediaInfo) : DownloaderUiState()
    data class Downloading(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloaderUiState()
    data class DownloadSuccess(val fileUri: Uri, val fileName: String, val format: DownloadFormat) : DownloaderUiState()
    data class Error(val message: String) : DownloaderUiState()
}

@HiltViewModel
class DownloaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloaderRepo: MediaDownloaderRepository
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _detectedPlatform = MutableStateFlow(MediaPlatform.UNKNOWN)
    val detectedPlatform: StateFlow<MediaPlatform> = _detectedPlatform.asStateFlow()

    private val _uiState = MutableStateFlow<DownloaderUiState>(DownloaderUiState.Idle)
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    private val _extractedInfo = MutableStateFlow<ExtractedMediaInfo?>(null)
    val extractedInfo: StateFlow<ExtractedMediaInfo?> = _extractedInfo.asStateFlow()

    private val _selectedOption = MutableStateFlow<MediaDownloadOption?>(null)
    val selectedOption: StateFlow<MediaDownloadOption?> = _selectedOption.asStateFlow()

    private var analyzeJob: kotlinx.coroutines.Job? = null
    private var downloadJob: kotlinx.coroutines.Job? = null

    fun onUrlInputChanged(newUrl: String) {
        val trimmed = newUrl.trim()
        _url.value = trimmed
        _detectedPlatform.value = MediaPlatform.detectFromUrl(trimmed)
    }

    fun analyze() {
        val inputUrl = _url.value.trim()
        if (inputUrl.isBlank()) {
            _uiState.value = DownloaderUiState.Error("Masukkan link media terlebih dahulu.")
            return
        }

        if (_uiState.value is DownloaderUiState.Analyzing) return

        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _uiState.value = DownloaderUiState.Analyzing
            _extractedInfo.value = null
            _selectedOption.value = null

            val result = downloaderRepo.extractMedia(inputUrl)
            result.fold(
                onSuccess = { info ->
                    _extractedInfo.value = info
                    _detectedPlatform.value = info.platform
                    _selectedOption.value = info.options.firstOrNull()
                    _uiState.value = DownloaderUiState.MediaReady(info)
                },
                onFailure = { error ->
                    _uiState.value = DownloaderUiState.Error(
                        error.localizedMessage ?: "Gagal menganalisis link. Pastikan link dapat diakses publik dan koneksi internet stabil."
                    )
                }
            )
        }
    }

    fun selectOption(option: MediaDownloadOption) {
        _selectedOption.value = option
    }

    fun startDownload() {
        if (_uiState.value is DownloaderUiState.Downloading) return

        val option = _selectedOption.value
        val info = _extractedInfo.value
        if (option == null || info == null) {
            _uiState.value = DownloaderUiState.Error("Pilih format/kualitas media terlebih dahulu.")
            return
        }

        val prefix = "${info.platform.name.lowercase()}_${option.quality ?: option.format.name.lowercase()}"

        UpscaleForegroundService.startDirect(
            context = context,
            mediaType = "DOWNLOAD",
            title = context.getString(R.string.notif_download_title),
            message = context.getString(R.string.notif_download_starting)
        )
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = {
            cancelDownload()
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloaderRepo.downloadMedia(option, prefix).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        _uiState.value = DownloaderUiState.Downloading(
                            percent = progress.percentage,
                            downloadedBytes = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes
                        )
                        val dlStr = formatSize(progress.bytesDownloaded)
                        val totStr = if (progress.totalBytes > 0) formatSize(progress.totalBytes) else "--"
                        UpscaleForegroundService.updateProgress(
                            context = context,
                            progress = progress.percentage,
                            title = context.getString(R.string.notif_download_title),
                            message = "${progress.percentage}% • $dlStr / $totStr",
                            mediaType = "DOWNLOAD",
                            isPaused = false
                        )
                    }
                    is DownloadProgress.Success -> {
                        UpscaleForegroundService.finishDirect(
                            context = context,
                            success = true,
                            title = context.getString(R.string.notif_download_success_title),
                            message = context.getString(R.string.notif_download_success_msg, progress.filePath),
                            outputUri = progress.fileUri
                        )
                        _uiState.value = DownloaderUiState.DownloadSuccess(
                            fileUri = progress.fileUri,
                            fileName = progress.filePath,
                            format = progress.format
                        )
                    }
                    is DownloadProgress.Error -> {
                        UpscaleForegroundService.finishDirect(
                            context = context,
                            success = false,
                            title = context.getString(R.string.notif_download_failed_title),
                            message = progress.message
                        )
                        _uiState.value = DownloaderUiState.Error(progress.message)
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        UpscaleForegroundService.stopDirect(context)
        _uiState.value = DownloaderUiState.Idle
    }

    fun resetState() {
        analyzeJob?.cancel()
        downloadJob?.cancel()
        UpscaleForegroundService.stopDirect(context)
        _uiState.value = DownloaderUiState.Idle
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    override fun onCleared() {
        super.onCleared()
        analyzeJob?.cancel()
        downloadJob?.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
    }
}
