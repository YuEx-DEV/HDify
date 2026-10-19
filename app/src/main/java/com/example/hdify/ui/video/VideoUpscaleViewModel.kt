package com.example.hdify.ui.video

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.R
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.usecase.UpscaleVideoUseCase
import com.example.hdify.domain.usecase.VideoUpscaleRequest
import com.example.hdify.domain.usecase.VideoUpscaleStatus
import com.example.hdify.logging.HdLogger
import com.example.hdify.service.UpscaleForegroundService
import com.example.hdify.video.VideoCapabilitiesHelper
import com.example.hdify.video.VideoMetadata
import com.example.hdify.video.VideoMetadataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class VideoUpscaleUiState {
    object Idle : VideoUpscaleUiState()
    data class Processing(
        val currentFrame: Int,
        val totalFrames: Int,
        val percent: Int,
        val fps: Double,
        val etaSec: Long,
        val isPaused: Boolean = false
    ) : VideoUpscaleUiState()
    data class Success(
        val outputUri: Uri,
        val originalUri: Uri,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val isPreview: Boolean,
        val tempFilePath: String? = null,
        val isSaved: Boolean = false
    ) : VideoUpscaleUiState()
    data class Error(val code: String, val message: String) : VideoUpscaleUiState()
}

@HiltViewModel
class VideoUpscaleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val upscaleVideoUseCase: UpscaleVideoUseCase
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _metadata = MutableStateFlow<VideoMetadata?>(null)
    val metadata: StateFlow<VideoMetadata?> = _metadata.asStateFlow()

    private val _scale = MutableStateFlow(2)
    val scale: StateFlow<Int> = _scale.asStateFlow()

    private val _isHevc = MutableStateFlow(VideoCapabilitiesHelper.hasHardwareHevcEncoder())
    val isHevc: StateFlow<Boolean> = _isHevc.asStateFlow()

    private val _qualityPercent = MutableStateFlow(85)
    val qualityPercent: StateFlow<Int> = _qualityPercent.asStateFlow()

    private val _interpolationEnabled = MutableStateFlow(false)
    val interpolationEnabled: StateFlow<Boolean> = _interpolationEnabled.asStateFlow()

    private val _interpolationFactor = MutableStateFlow(2)
    val interpolationFactor: StateFlow<Int> = _interpolationFactor.asStateFlow()

    private val _uhdMode = MutableStateFlow(false)
    val uhdMode: StateFlow<Boolean> = _uhdMode.asStateFlow()

    private val _fpsWarning = MutableStateFlow<String?>(null)
    val fpsWarning: StateFlow<String?> = _fpsWarning.asStateFlow()

    private val _encoderWarning = MutableStateFlow<String?>(null)
    val encoderWarning: StateFlow<String?> = _encoderWarning.asStateFlow()

    private val _uiState = MutableStateFlow<VideoUpscaleUiState>(VideoUpscaleUiState.Idle)
    val uiState: StateFlow<VideoUpscaleUiState> = _uiState.asStateFlow()

    private var upscaleJob: kotlinx.coroutines.Job? = null
    private var isPaused = false

    fun onVideoSelected(uri: Uri) {
        _selectedUri.value = uri
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                VideoMetadataExtractor.extract(context, uri)
            }
            _metadata.value = meta
            if (meta != null && (meta.displayWidth >= 2048 || meta.displayHeight >= 2048)) {
                _uhdMode.value = true
            }
            validateSettings()
        }
    }

    fun setScale(newScale: Int) {
        _scale.value = newScale
        validateSettings()
    }

    fun setIsHevc(useHevc: Boolean) {
        _isHevc.value = useHevc
        validateSettings()
    }

    fun setQualityPercent(quality: Int) {
        _qualityPercent.value = quality
    }

    fun setInterpolationEnabled(enabled: Boolean) {
        _interpolationEnabled.value = enabled
        validateSettings()
    }

    fun setInterpolationFactor(factor: Int) {
        _interpolationFactor.value = factor
        validateSettings()
    }

    fun setUhdMode(enabled: Boolean) {
        _uhdMode.value = enabled
    }

    private fun validateSettings() {
        val meta = _metadata.value ?: return
        val targetW = meta.width * _scale.value
        val targetH = meta.height * _scale.value
        val mime = if (_isHevc.value) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        val enc = VideoCapabilitiesHelper.findEncoder(mime)
        if (enc == null) {
            _encoderWarning.value = "Hardware encoder tidak ditemukan untuk codec yang dipilih."
            return
        }

        val maxEncDim = maxOf(enc.maxWidth, enc.maxHeight)
        val minEncDim = minOf(enc.maxWidth, enc.maxHeight)
        val maxReqDim = maxOf(targetW, targetH)
        val minReqDim = minOf(targetW, targetH)

        if (maxReqDim > maxEncDim || minReqDim > minEncDim) {
            _encoderWarning.value = "Resolusi target ${targetW}x${targetH} melebihi batas encoder (${enc.maxWidth}x${enc.maxHeight})."
        } else {
            _encoderWarning.value = null
        }

        if (_interpolationEnabled.value) {
            val rawTargetFps = meta.fps * _interpolationFactor.value
            val maxAllowedFps = enc.maxFrameRate.coerceAtMost(120)
            if (rawTargetFps > maxAllowedFps) {
                _fpsWarning.value = "Target FPS (%.1f) melebihi batas encoder (%d FPS) atau batas maksimal 120 FPS. Target akan dibatasi secara otomatis.".format(rawTargetFps, maxAllowedFps)
            } else {
                _fpsWarning.value = null
            }
        } else {
            _fpsWarning.value = null
        }
    }

    fun startUpscale(isPreview3s: Boolean = false) {
        val uri = _selectedUri.value ?: return
        if (_uiState.value is VideoUpscaleUiState.Processing) return

        if (_scale.value <= 1 && !_interpolationEnabled.value) {
            _uiState.value = VideoUpscaleUiState.Error("HD-REQ-001", "Pilih minimal salah satu fitur: Upscale (> 1x) atau Interpolasi Frame (x2/x3/x4)")
            return
        }

        isPaused = false
        val request = VideoUpscaleRequest(
            sourceUri = uri,
            scale = _scale.value,
            engineType = EngineType.REAL_ESRGAN,
            modelDir = if (_scale.value > 1) "realesr-animevideov3-x${_scale.value}" else "",
            isHevc = _isHevc.value,
            qualityPercent = _qualityPercent.value,
            isPreview3s = isPreview3s,
            interpolationFactor = if (_interpolationEnabled.value) _interpolationFactor.value else 1,
            rifeModelDir = "models/rife/rife-v4.6",
            maxFps = 120,
            uhdMode = _uhdMode.value
        )

        val scaleVal = _scale.value
        if (!isPreview3s) {
            UpscaleForegroundService.startDirect(
                context = context,
                mediaType = "VIDEO",
                title = context.getString(R.string.notif_video_processing_title, scaleVal),
                message = context.getString(R.string.notif_preparing)
            )
            UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = {
                cancelUpscale()
            }
            UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = { paused: Boolean ->
                if (paused != isPaused) {
                    togglePause()
                }
            }
        }

        upscaleJob?.cancel()
        upscaleJob = viewModelScope.launch {
            try {
                _uiState.value = VideoUpscaleUiState.Processing(0, 0, 0, 0.0, 0L)

                val status = upscaleVideoUseCase.execute(request) { progress ->
                    _uiState.value = VideoUpscaleUiState.Processing(
                        progress.currentFrame,
                        progress.totalFrames,
                        progress.percent,
                        progress.fps,
                        progress.etaSeconds,
                        isPaused
                    )
                    if (!isPreview3s) {
                        val etaText = if (progress.etaSeconds > 0) " • ETA ${progress.etaSeconds}s (%.1f FPS)".format(progress.fps) else ""
                        UpscaleForegroundService.updateProgress(
                            context = context,
                            progress = progress.percent,
                            title = context.getString(R.string.notif_video_processing_title, scaleVal),
                            message = "Frame ${progress.currentFrame}/${progress.totalFrames} (${progress.percent}%)$etaText",
                            mediaType = "VIDEO",
                            isPaused = isPaused
                        )
                    }
                }

                when (status) {
                    is VideoUpscaleStatus.Success -> {
                        if (!isPreview3s) {
                            UpscaleForegroundService.finishDirect(
                                context = context,
                                success = true,
                                title = context.getString(R.string.notif_completed_title),
                                message = context.getString(R.string.notif_completed_video_msg),
                                outputUri = status.outputUri
                            )
                        }
                        _uiState.value = VideoUpscaleUiState.Success(
                            outputUri = status.outputUri,
                            originalUri = uri,
                            width = status.width,
                            height = status.height,
                            durationMs = status.durationMs,
                            isPreview = status.isPreview,
                            tempFilePath = status.tempFilePath,
                            isSaved = status.isSaved
                        )
                    }
                    is VideoUpscaleStatus.Canceled -> {
                        if (!isPreview3s) {
                            UpscaleForegroundService.stopDirect(context)
                        }
                        _uiState.value = VideoUpscaleUiState.Idle
                    }
                    is VideoUpscaleStatus.Error -> {
                        if (!isPreview3s) {
                            UpscaleForegroundService.finishDirect(
                                context = context,
                                success = false,
                                title = context.getString(R.string.notif_video_failed_title),
                                message = status.message
                            )
                        }
                        _uiState.value = VideoUpscaleUiState.Error(status.code, status.message)
                    }
                    is VideoUpscaleStatus.Progress -> {}
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (!isPreview3s) {
                    UpscaleForegroundService.stopDirect(context)
                }
                throw ce
            } catch (e: Exception) {
                HdLogger.e(TAG, "Unexpected error in video upscale: ${e.message}", e)
                if (!isPreview3s) {
                    UpscaleForegroundService.finishDirect(
                        context = context,
                        success = false,
                        title = context.getString(R.string.notif_video_failed_title),
                        message = e.message ?: context.getString(R.string.error_generic)
                    )
                }
                _uiState.value = VideoUpscaleUiState.Error("HD-VID-500", e.message ?: "Terjadi kesalahan saat memproses video")
            } finally {
                UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
                UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = null
            }
        }
    }

    fun togglePause() {
        val current = _uiState.value
        if (current is VideoUpscaleUiState.Processing) {
            isPaused = !isPaused
            if (isPaused) {
                upscaleVideoUseCase.pause()
            } else {
                upscaleVideoUseCase.resume()
            }
            _uiState.value = current.copy(isPaused = isPaused)
            UpscaleForegroundService.updateProgress(
                context = context,
                progress = current.percent,
                title = if (isPaused) context.getString(R.string.notif_paused_title) else context.getString(R.string.notif_video_processing_title, _scale.value),
                message = if (isPaused) "Proses dijeda" else "Frame ${current.currentFrame}/${current.totalFrames}",
                isVideo = true,
                isPaused = isPaused
            )
        }
    }

    fun cancelUpscale() {
        upscaleVideoUseCase.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
        UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = null
        _uiState.value = VideoUpscaleUiState.Idle
    }

    fun resetState() {
        _uiState.value = VideoUpscaleUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        upscaleVideoUseCase.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
        UpscaleForegroundService.ServiceControlRegistry.onPauseResumeDirect = null
    }

    companion object {
        private const val TAG = "VideoUpscaleVM"
    }
}
