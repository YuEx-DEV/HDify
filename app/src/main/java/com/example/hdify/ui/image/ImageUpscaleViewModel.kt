package com.example.hdify.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import com.example.hdify.domain.model.ContentType
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.model.Preset
import com.example.hdify.domain.usecase.ImageUpscaleStatus
import com.example.hdify.domain.usecase.UpscaleImageUseCase
import com.example.hdify.domain.usecase.UpscaleRequest
import com.example.hdify.engine.UpscaleEngineFactory
import com.example.hdify.logging.HdLogger
import com.example.hdify.service.UpscaleForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImageUpscaleUiState {
    object Idle : ImageUpscaleUiState()
    data class Processing(val current: Int, val total: Int, val percent: Int) : ImageUpscaleUiState()
    data class Success(
        val outputUri: Uri,
        val originalUri: Uri,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val backend: String,
        val deviceName: String
    ) : ImageUpscaleUiState()
    data class CropPreviewReady(val before: Bitmap, val after: Bitmap) : ImageUpscaleUiState()
    data class BatchEnqueued(val count: Int) : ImageUpscaleUiState()
    data class Error(val code: String, val message: String) : ImageUpscaleUiState()
}

@HiltViewModel
class ImageUpscaleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val upscaleUseCase: UpscaleImageUseCase,
    private val engineFactory: UpscaleEngineFactory,
    private val jobDao: JobDao
) : ViewModel() {

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    private val _sourceDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val sourceDimensions: StateFlow<Pair<Int, Int>?> = _sourceDimensions.asStateFlow()

    private val _contentType = MutableStateFlow(ContentType.ANIME)
    val contentType: StateFlow<ContentType> = _contentType.asStateFlow()

    private val _preset = MutableStateFlow(Preset.BALANCED)
    val preset: StateFlow<Preset> = _preset.asStateFlow()

    private val _scale = MutableStateFlow(2)
    val scale: StateFlow<Int> = _scale.asStateFlow()

    private val _engineType = MutableStateFlow(EngineType.REAL_CUGAN)
    val engineType: StateFlow<EngineType> = _engineType.asStateFlow()

    private val _modelDir = MutableStateFlow("models-se")
    val modelDir: StateFlow<String> = _modelDir.asStateFlow()

    private val _denoise = MutableStateFlow(0)
    val denoise: StateFlow<Int> = _denoise.asStateFlow()

    private val _tileSize = MutableStateFlow(0)
    val tileSize: StateFlow<Int> = _tileSize.asStateFlow()

    private val _useFp16 = MutableStateFlow(true)
    val useFp16: StateFlow<Boolean> = _useFp16.asStateFlow()

    private val _useTta = MutableStateFlow(false)
    val useTta: StateFlow<Boolean> = _useTta.asStateFlow()

    private val _isCustomSizeEnabled = MutableStateFlow(false)
    val isCustomSizeEnabled: StateFlow<Boolean> = _isCustomSizeEnabled.asStateFlow()

    private val _customWidth = MutableStateFlow(0)
    val customWidth: StateFlow<Int> = _customWidth.asStateFlow()

    private val _customHeight = MutableStateFlow(0)
    val customHeight: StateFlow<Int> = _customHeight.asStateFlow()

    private val _uiState = MutableStateFlow<ImageUpscaleUiState>(ImageUpscaleUiState.Idle)
    val uiState: StateFlow<ImageUpscaleUiState> = _uiState.asStateFlow()

    private var upscaleJob: kotlinx.coroutines.Job? = null

    fun onImagesSelected(uris: List<Uri>) {
        _selectedUris.value = uris
        val primaryUri = uris.firstOrNull()
        if (primaryUri != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(primaryUri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            _sourceDimensions.value = Pair(options.outWidth, options.outHeight)
                        } else {
                            _sourceDimensions.value = null
                        }
                    }
                } catch (e: Exception) {
                    HdLogger.w(TAG, "Failed to inspect bitmap dimensions: ${e.message}")
                    _sourceDimensions.value = null
                }
            }
        } else {
            _sourceDimensions.value = null
        }
    }

    fun setContentType(type: ContentType) {
        _contentType.value = type
        syncEngineAndModel()
    }

    fun setPreset(newPreset: Preset) {
        _preset.value = newPreset
        syncEngineAndModel()
    }

    fun setScale(newScale: Int) {
        _scale.value = newScale
        // Pro models do not support native 4x
        if (newScale == 4 && _modelDir.value == "models-pro") {
            _modelDir.value = "models-se"
        }
        if (newScale >= 3 && _denoise.value in listOf(1, 2)) {
            _denoise.value = 3
        }
    }

    fun setModelDir(newDir: String) {
        _modelDir.value = newDir
        if (newDir == "models-pro") {
            if (_scale.value == 4) {
                _scale.value = 2
            }
            if (_denoise.value !in listOf(-1, 0, 3)) {
                _denoise.value = 0
            }
        }
    }

    fun setDenoise(newDenoise: Int) {
        var d = newDenoise
        if (_modelDir.value == "models-pro" && d !in listOf(-1, 0, 3)) {
            d = 0
        } else if (_scale.value >= 3 && d in listOf(1, 2)) {
            d = 3
        }
        _denoise.value = d
    }

    fun setCustomSizeEnabled(enabled: Boolean) {
        _isCustomSizeEnabled.value = enabled
    }

    fun setCustomDimensions(width: Int, height: Int) {
        _customWidth.value = width
        _customHeight.value = height
    }

    fun applyAdvancedSettings(data: AdvancedSettingsData) {
        _engineType.value = data.engineType
        _modelDir.value = data.modelDir
        _tileSize.value = data.tileSize
        _useFp16.value = data.useFp16
        _useTta.value = data.useTta
    }

    private fun syncEngineAndModel() {
        val config = engineFactory.createDefaultConfig(
            contentType = _contentType.value,
            preset = _preset.value,
            scale = _scale.value
        )
        _engineType.value = config.engine
        _modelDir.value = config.modelDirectory
        _denoise.value = config.denoise
    }

    fun startUpscale(isCropPreview: Boolean = false) {
        val uris = _selectedUris.value
        if (uris.isEmpty()) return

        if (uris.size > 1 && !isCropPreview) {
            enqueueBatchJobs()
            return
        }

        val primaryUri = uris.first()
        if (_uiState.value is ImageUpscaleUiState.Processing) return

        val request = UpscaleRequest(
            sourceUri = primaryUri,
            scale = _scale.value,
            engineType = _engineType.value,
            modelDir = _modelDir.value,
            denoise = _denoise.value,
            tileSize = _tileSize.value,
            useFp16 = _useFp16.value,
            isCropPreview = isCropPreview,
            customTargetWidth = if (_isCustomSizeEnabled.value) _customWidth.value else 0,
            customTargetHeight = if (_isCustomSizeEnabled.value) _customHeight.value else 0
        )

        val scaleVal = _scale.value
        val startTime = System.currentTimeMillis()
        if (!isCropPreview) {
            UpscaleForegroundService.startDirect(
                context = context,
                mediaType = "IMAGE",
                title = context.getString(com.example.hdify.R.string.notif_image_processing_title, scaleVal),
                message = context.getString(com.example.hdify.R.string.notif_preparing)
            )
            UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = {
                cancelUpscale()
            }
        }

        upscaleJob?.cancel()
        upscaleJob = viewModelScope.launch {
            try {
                _uiState.value = ImageUpscaleUiState.Processing(0, 0, 0)

                val status = upscaleUseCase.execute(request) { progress ->
                    _uiState.value = ImageUpscaleUiState.Processing(progress.current, progress.total, progress.percent)
                    if (!isCropPreview) {
                        val now = System.currentTimeMillis()
                        val elapsed = (now - startTime).coerceAtLeast(1)
                        val etaMs = if (progress.percent > 0) ((elapsed * (100 - progress.percent)) / progress.percent) else 0L
                        val etaSec = (etaMs / 1000).toInt()
                        val etaText = if (etaSec > 0) " • ETA ${etaSec}s" else ""
                        UpscaleForegroundService.updateProgress(
                            context = context,
                            progress = progress.percent,
                            title = context.getString(com.example.hdify.R.string.notif_image_processing_title, scaleVal),
                            message = "Tile ${progress.current}/${progress.total} (${progress.percent}%)$etaText",
                            isVideo = false
                        )
                    }
                }

                when (status) {
                    is ImageUpscaleStatus.Success -> {
                        if (isCropPreview) {
                            try {
                                val fullBmp = context.contentResolver.openInputStream(primaryUri)?.use {
                                    BitmapFactory.decodeStream(it)
                                } ?: throw IllegalStateException("Tidak dapat membuka file gambar asli")
                                val cropW = minOf(256, fullBmp.width)
                                val cropH = minOf(256, fullBmp.height)
                                val beforeCrop = Bitmap.createBitmap(fullBmp, (fullBmp.width - cropW) / 2, (fullBmp.height - cropH) / 2, cropW, cropH)
                                if (fullBmp != beforeCrop) {
                                    fullBmp.recycle()
                                }
                                val afterCrop = BitmapFactory.decodeFile(status.outputUri.path)
                                _uiState.value = ImageUpscaleUiState.CropPreviewReady(beforeCrop, afterCrop)
                            } catch (e: Exception) {
                                _uiState.value = ImageUpscaleUiState.Error("HD-IMG-301", "Gagal memuat pratinjau crop: ${e.message}")
                            }
                        } else {
                            UpscaleForegroundService.finishDirect(
                                context = context,
                                success = true,
                                title = context.getString(com.example.hdify.R.string.notif_completed_title),
                                message = context.getString(com.example.hdify.R.string.notif_completed_image_msg, status.width, status.height),
                                outputUri = status.outputUri
                            )
                            _uiState.value = ImageUpscaleUiState.Success(
                                outputUri = status.outputUri,
                                originalUri = primaryUri,
                                width = status.width,
                                height = status.height,
                                durationMs = status.durationMs,
                                backend = status.backend,
                                deviceName = status.deviceName
                            )
                        }
                    }
                    is ImageUpscaleStatus.Canceled -> {
                        if (!isCropPreview) {
                            UpscaleForegroundService.stopDirect(context)
                        }
                        _uiState.value = ImageUpscaleUiState.Idle
                    }
                    is ImageUpscaleStatus.Error -> {
                        if (!isCropPreview) {
                            UpscaleForegroundService.finishDirect(
                                context = context,
                                success = false,
                                title = context.getString(com.example.hdify.R.string.notif_image_failed_title),
                                message = status.message
                            )
                        }
                        _uiState.value = ImageUpscaleUiState.Error(status.code, status.message)
                    }
                    is ImageUpscaleStatus.Progress -> {
                        // Handled in callback
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (!isCropPreview) {
                    UpscaleForegroundService.stopDirect(context)
                }
                throw ce
            } catch (e: Exception) {
                HdLogger.e(TAG, "Unexpected error in image upscale: ${e.message}", e)
                if (!isCropPreview) {
                    UpscaleForegroundService.finishDirect(
                        context = context,
                        success = false,
                        title = context.getString(com.example.hdify.R.string.notif_image_failed_title),
                        message = e.message ?: context.getString(com.example.hdify.R.string.error_generic)
                    )
                }
                _uiState.value = ImageUpscaleUiState.Error("HD-IMG-500", e.message ?: "Terjadi kesalahan saat memproses gambar")
            } finally {
                UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
            }
        }
    }

    fun enqueueBatchJobs() {
        val uris = _selectedUris.value
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val engineNameStr = if (_engineType.value == EngineType.REAL_CUGAN) {
                "Real-CUGAN (${_modelDir.value})"
            } else {
                "Real-ESRGAN (${_modelDir.value})"
            }

            for (uri in uris) {
                jobDao.insertJob(
                    JobEntity(
                        mediaType = "IMAGE",
                        sourceUri = uri.toString(),
                        outputUri = null,
                        scale = _scale.value,
                        engineName = engineNameStr,
                        status = "PENDING",
                        progress = 0
                    )
                )
            }

            UpscaleForegroundService.start(context)
            _uiState.value = ImageUpscaleUiState.BatchEnqueued(uris.size)
        }
    }

    fun cancelUpscale() {
        upscaleUseCase.cancel()
        upscaleJob?.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
        _uiState.value = ImageUpscaleUiState.Idle
    }

    fun resetState() {
        _uiState.value = ImageUpscaleUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        upscaleJob?.cancel()
        upscaleUseCase.cancel()
        UpscaleForegroundService.stopDirect(context)
        UpscaleForegroundService.ServiceControlRegistry.onCancelDirect = null
    }

    companion object {
        private const val TAG = "ImageUpscaleVM"
    }
}
