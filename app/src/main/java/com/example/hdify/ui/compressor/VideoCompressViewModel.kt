package com.example.hdify.ui.compressor

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.R
import com.example.hdify.logging.HdLogger
import com.example.hdify.service.UpscaleForegroundService
import com.example.hdify.video.VideoMetadata
import com.example.hdify.video.VideoMetadataExtractor
import com.example.hdify.video.compressor.CompressPreset
import com.example.hdify.video.compressor.VideoCompressorConfig
import com.example.hdify.video.compressor.VideoCompressorProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class CompressUiState {
    object Idle : CompressUiState()
    data class Compressing(
        val currentFrame: Int,
        val totalFrames: Int,
        val percent: Int,
        val fps: Double,
        val etaSec: Long,
        val isPaused: Boolean = false
    ) : CompressUiState()
    data class Success(
        val outputUri: Uri,
        val originalSize: Long,
        val compressedSize: Long,
        val reductionPercent: Int,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val preset: CompressPreset
    ) : CompressUiState()
    data class Error(val message: String) : CompressUiState()
}

@HiltViewModel
class VideoCompressViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _metadata = MutableStateFlow<VideoMetadata?>(null)
    val metadata: StateFlow<VideoMetadata?> = _metadata.asStateFlow()

    private val _originalFileSize = MutableStateFlow<Long>(0L)
    val originalFileSize: StateFlow<Long> = _originalFileSize.asStateFlow()

    private val _selectedPreset = MutableStateFlow(CompressPreset.ORIGINAL_BALANCED)
    val selectedPreset: StateFlow<CompressPreset> = _selectedPreset.asStateFlow()

    private val _customBitrateMbps = MutableStateFlow(10.0f)
    val customBitrateMbps: StateFlow<Float> = _customBitrateMbps.asStateFlow()

    private val _customMaxDimension = MutableStateFlow(9999)
    val customMaxDimension: StateFlow<Int> = _customMaxDimension.asStateFlow()

    private val _customMime = MutableStateFlow(MediaFormat.MIMETYPE_VIDEO_AVC)
    val customMime: StateFlow<String> = _customMime.asStateFlow()

    private val _keepOriginalFps = MutableStateFlow(true)
    val keepOriginalFps: StateFlow<Boolean> = _keepOriginalFps.asStateFlow()

    private val _forceHdr10Plus = MutableStateFlow(false)
    val forceHdr10Plus: StateFlow<Boolean> = _forceHdr10Plus.asStateFlow()

    private val _estimatedSizeBytes = MutableStateFlow(0L)
    val estimatedSizeBytes: StateFlow<Long> = _estimatedSizeBytes.asStateFlow()

    private val _estimatedSavingsPercent = MutableStateFlow(0)
    val estimatedSavingsPercent: StateFlow<Int> = _estimatedSavingsPercent.asStateFlow()

    private val _currentConfig = MutableStateFlow<VideoCompressorConfig?>(null)
    val currentConfig: StateFlow<VideoCompressorConfig?> = _currentConfig.asStateFlow()

    private val _uiState = MutableStateFlow<CompressUiState>(CompressUiState.Idle)
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    private var activeProcessor: VideoCompressorProcessor? = null

    fun setVideoUri(uri: Uri) {
        _selectedUri.value = uri
        _uiState.value = CompressUiState.Idle
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                VideoMetadataExtractor.extract(context, uri)
            }
            _metadata.value = meta
            val size = getFileSizeWithFallback(uri, meta)
            _originalFileSize.value = size
            recalculateEstimation()
        }
    }

    fun selectPreset(preset: CompressPreset) {
        _selectedPreset.value = preset
        recalculateEstimation()
    }

    fun setCustomBitrate(mbps: Float) {
        _customBitrateMbps.value = mbps
        recalculateEstimation()
    }

    fun setCustomMaxDimension(dim: Int) {
        _customMaxDimension.value = dim
        recalculateEstimation()
    }

    fun setCustomMime(mime: String) {
        _customMime.value = mime
        recalculateEstimation()
    }

    fun setKeepOriginalFps(keep: Boolean) {
        _keepOriginalFps.value = keep
        recalculateEstimation()
    }

    fun setForceHdr10Plus(enabled: Boolean) {
        _forceHdr10Plus.value = enabled
        recalculateEstimation()
    }

    private fun recalculateEstimation() {
        val meta = _metadata.value ?: return
        val preset = _selectedPreset.value
        val config = VideoCompressorConfig.fromPreset(
            preset = preset,
            meta = meta,
            originalFileSize = _originalFileSize.value,
            customBitrateMbps = _customBitrateMbps.value,
            customMaxDimension = _customMaxDimension.value,
            customMime = _customMime.value,
            keepOriginalFps = _keepOriginalFps.value,
            forceHdr10Plus = _forceHdr10Plus.value
        )
        _currentConfig.value = config

        val estBytes = VideoCompressorConfig.estimateOutputSizeBytes(config.bitrateBps, meta.durationUs)
        _estimatedSizeBytes.value = estBytes

        val origSize = _originalFileSize.value
        if (origSize > 0) {
            val savings = (((origSize - estBytes).toDouble() / origSize.toDouble()) * 100).toInt()
            _estimatedSavingsPercent.value = savings.coerceAtLeast(0)
        } else {
            _estimatedSavingsPercent.value = 0
        }
    }

    fun startCompress() {
        val uri = _selectedUri.value ?: return
        val meta = _metadata.value ?: return
        val preset = _selectedPreset.value

        val config = VideoCompressorConfig.fromPreset(
            preset = preset,
            meta = meta,
            originalFileSize = _originalFileSize.value,
            customBitrateMbps = _customBitrateMbps.value,
            customMaxDimension = _customMaxDimension.value,
            customMime = _customMime.value,
            keepOriginalFps = _keepOriginalFps.value,
            forceHdr10Plus = _forceHdr10Plus.value
        )

        val processor = VideoCompressorProcessor()
        activeProcessor = processor

        UpscaleForegroundService.startDirect(
            context = context,
            mediaType = "COMPRESS",
            title = context.getString(R.string.notif_compress_title),
            message = context.getString(R.string.notif_compress_preparing)
        )
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = {
            cancelCompress()
        }
        UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = {
            togglePause()
        }

        _uiState.value = CompressUiState.Compressing(
            currentFrame = 0,
            totalFrames = meta.totalFrames.toInt(),
            percent = 0,
            fps = 0.0,
            etaSec = 0,
            isPaused = false
        )

        viewModelScope.launch {
            try {
                val outputUri = processor.compress(
                    context = context,
                    sourceUri = uri,
                    meta = meta,
                    config = config,
                    onProgress = { cur, tot, pct, fps, eta ->
                        val isPaused = processor.isPaused()
                        _uiState.value = CompressUiState.Compressing(cur, tot, pct, fps, eta, isPaused)
                        val etaText = if (eta > 0) " • ETA ${eta}s (%.1f FPS)".format(fps) else ""
                        UpscaleForegroundService.updateProgress(
                            context = context,
                            progress = pct,
                            title = context.getString(R.string.notif_compress_title),
                            message = "Frame $cur/$tot ($pct%)$etaText",
                            mediaType = "COMPRESS",
                            isPaused = isPaused
                        )
                    }
                )

                val origSize = _originalFileSize.value
                val compressedSize = getFileSizeWithFallback(outputUri, null)
                val reduction = if (origSize > 0 && origSize >= compressedSize) {
                    (((origSize - compressedSize).toDouble() / origSize.toDouble()) * 100).toInt()
                } else 0

                UpscaleForegroundService.finishDirect(
                    context = context,
                    success = true,
                    title = context.getString(R.string.notif_compress_success_title),
                    message = context.getString(
                        R.string.notif_compress_success_msg,
                        reduction,
                        VideoCompressorConfig.formatFileSize(compressedSize)
                    ),
                    outputUri = outputUri
                )

                _uiState.value = CompressUiState.Success(
                    outputUri = outputUri,
                    originalSize = origSize,
                    compressedSize = compressedSize,
                    reductionPercent = reduction,
                    durationMs = meta.durationMs,
                    width = config.targetWidth,
                    height = config.targetHeight,
                    preset = preset
                )
            } catch (t: Throwable) {
                if (t is InterruptedException) {
                    UpscaleForegroundService.stopDirect(context)
                    _uiState.value = CompressUiState.Idle
                } else {
                    HdLogger.e("VideoCompressVM", "Compression failed: ${t.message}", t)
                    UpscaleForegroundService.finishDirect(
                        context = context,
                        success = false,
                        title = context.getString(R.string.notif_compress_failed_title),
                        message = t.message ?: "Gagal mengompres video"
                    )
                    _uiState.value = CompressUiState.Error(t.message ?: "Gagal mengompres video")
                }
            } finally {
                activeProcessor = null
                UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
                UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = null
            }
        }
    }

    fun cancelCompress() {
        activeProcessor?.cancel()
        UpscaleForegroundService.stopDirect(context)
        _uiState.value = CompressUiState.Idle
    }

    fun togglePause() {
        val proc = activeProcessor ?: return
        if (proc.isPaused()) {
            proc.resume()
        } else {
            proc.pause()
        }
        val isPausedNow = proc.isPaused()
        val current = _uiState.value
        if (current is CompressUiState.Compressing) {
            _uiState.value = current.copy(isPaused = isPausedNow)
            UpscaleForegroundService.updateProgress(
                context = context,
                progress = current.percent,
                title = if (isPausedNow) context.getString(R.string.notif_paused_title) else context.getString(R.string.notif_compress_title),
                message = if (isPausedNow) "Proses dijeda" else "Frame ${current.currentFrame}/${current.totalFrames}",
                mediaType = "COMPRESS",
                isPaused = isPausedNow
            )
        }
    }

    fun reset() {
        _selectedUri.value = null
        _metadata.value = null
        _originalFileSize.value = 0L
        _uiState.value = CompressUiState.Idle
    }

    private fun getFileSizeWithFallback(uri: Uri, meta: VideoMetadata?): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size > 0L) return size
            }
        } catch (_: Exception) {}

        if (meta != null && meta.durationUs > 0L && meta.bitrate > 0L) {
            return (meta.bitrate * (meta.durationUs / 1_000_000.0) / 8.0).toLong()
        }
        return 0L
    }

    override fun onCleared() {
        super.onCleared()
        activeProcessor?.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
        UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = null
    }
}
