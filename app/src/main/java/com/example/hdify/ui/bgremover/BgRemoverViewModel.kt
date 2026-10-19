package com.example.hdify.ui.bgremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.R
import com.example.hdify.domain.usecase.BackgroundRemoverUseCase
import com.example.hdify.logging.HdLogger
import com.example.hdify.service.UpscaleForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class BgRemoverUiState {
    object Idle : BgRemoverUiState()
    object Processing : BgRemoverUiState()
    data class Ready(
        val source: Bitmap,
        val mask: Bitmap,
        val isSaved: Boolean = false,
        val isAutoRemoved: Boolean = false
    ) : BgRemoverUiState()
    data class Error(val message: String) : BgRemoverUiState()
}

@HiltViewModel
class BgRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val useCase: BackgroundRemoverUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "BgRemoverViewModel"
        const val COLOR_TRANSPARENT = Color.TRANSPARENT
        const val COLOR_WHITE = Color.WHITE
        val COLOR_RED_PAS_FOTO = Color.parseColor("#D32F2F")
        val COLOR_BLUE_PAS_FOTO = Color.parseColor("#1976D2")
        const val COLOR_BLACK = Color.BLACK
    }

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _selectedBgColor = MutableStateFlow(COLOR_TRANSPARENT)
    val selectedBgColor: StateFlow<Int> = _selectedBgColor.asStateFlow()

    private val _customColor = MutableStateFlow<Int?>(null)
    val customColor: StateFlow<Int?> = _customColor.asStateFlow()

    private val _brushMode = MutableStateFlow(BrushMode.MAGIC_COLOR)
    val brushMode: StateFlow<BrushMode> = _brushMode.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isAutoRemoving = MutableStateFlow(false)
    val isAutoRemoving: StateFlow<Boolean> = _isAutoRemoving.asStateFlow()

    private val _uiState = MutableStateFlow<BgRemoverUiState>(BgRemoverUiState.Idle)
    val uiState: StateFlow<BgRemoverUiState> = _uiState.asStateFlow()

    private var sourceBitmap: Bitmap? = null
    private var initialMaskBitmap: Bitmap? = null

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _uiState.value = BgRemoverUiState.Processing

        viewModelScope.launch {
            try {
                val bmp = loadSampledBitmap(uri, maxDim = 2048)
                if (bmp == null) {
                    _uiState.value = BgRemoverUiState.Error("Gagal membuka file gambar.")
                    return@launch
                }
                sourceBitmap = bmp

                // Create initial full white mask (entire image visible, giving user choice to auto-remove)
                val fullMask = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                }
                initialMaskBitmap = fullMask

                _uiState.value = BgRemoverUiState.Ready(bmp, fullMask, isSaved = false, isAutoRemoved = false)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal memproses gambar: ${e.message}", e)
                _uiState.value = BgRemoverUiState.Error(e.message ?: "Terjadi kesalahan saat memproses gambar.")
            }
        }
    }

    fun runAutoRemove(onComplete: ((Bitmap?) -> Unit)? = null) {
        val bmp = sourceBitmap ?: return
        _isAutoRemoving.value = true
        viewModelScope.launch {
            try {
                val mask = useCase.generateInitialMask(bmp)
                initialMaskBitmap = mask
                onComplete?.invoke(mask)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal auto remove background: ${e.message}", e)
                onComplete?.invoke(null)
            } finally {
                _isAutoRemoving.value = false
            }
        }
    }

    fun setBackgroundColor(color: Int) {
        _selectedBgColor.value = color
    }

    fun setCustomColor(color: Int) {
        _customColor.value = color
        _selectedBgColor.value = color
    }

    fun setBrushMode(mode: BrushMode) {
        _brushMode.value = mode
    }

    fun updateHistoryState(undoAvailable: Boolean, redoAvailable: Boolean) {
        _canUndo.value = undoAvailable
        _canRedo.value = redoAvailable
    }

    fun saveToGallery(currentMask: Bitmap, onComplete: (Boolean, Uri?) -> Unit) {
        val src = sourceBitmap ?: return
        val bgColor = _selectedBgColor.value
        val isTransparent = (bgColor == COLOR_TRANSPARENT)

        viewModelScope.launch {
            try {
                val composite = useCase.renderComposite(src, currentMask, bgColor)
                val savedUri = useCase.saveBitmapToGallery(composite, isTransparent)
                val currentState = _uiState.value
                if (currentState is BgRemoverUiState.Ready) {
                    _uiState.value = currentState.copy(isSaved = true)
                }
                UpscaleForegroundService.showCompletionNotification(
                    context = context,
                    title = context.getString(R.string.notif_bg_removed_saved_title),
                    message = context.getString(R.string.notif_bg_removed_saved_msg),
                    outputUriStr = savedUri?.toString(),
                    iconRes = R.drawable.ic_auto_awesome
                )
                onComplete(true, savedUri)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menyimpan ke galeri: ${e.message}", e)
                UpscaleForegroundService.showFailureNotification(
                    context = context,
                    title = context.getString(R.string.notif_bg_removed_failed_title),
                    message = e.message ?: "Gagal menyimpan gambar"
                )
                onComplete(false, null)
            }
        }
    }

    fun prepareForUpscaler(currentMask: Bitmap, onReady: (Uri?) -> Unit) {
        val src = sourceBitmap ?: return
        val bgColor = _selectedBgColor.value

        viewModelScope.launch {
            try {
                val composite = useCase.renderComposite(src, currentMask, bgColor)
                val tempUri = useCase.saveTempBitmapForUpscale(composite)
                onReady(tempUri)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menyiapkan gambar untuk upscaler: ${e.message}", e)
                onReady(null)
            }
        }
    }

    private suspend fun loadSampledBitmap(uri: Uri, maxDim: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sample > maxDim) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            HdLogger.e(TAG, "Gagal decode bitmap: ${e.message}", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { sourceBitmap?.recycle() } catch (_: Throwable) {}
        try { initialMaskBitmap?.recycle() } catch (_: Throwable) {}
        sourceBitmap = null
        initialMaskBitmap = null
    }
}
