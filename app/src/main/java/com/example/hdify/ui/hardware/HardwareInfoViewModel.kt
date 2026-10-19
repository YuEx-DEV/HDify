package com.example.hdify.ui.hardware

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.hardware.HardwareInfoRepository
import com.example.hdify.domain.model.HardwareCapabilities
import com.example.hdify.engine.NativeBridge
import com.example.hdify.logging.HdLogger
import com.example.hdify.logging.LogZipExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface HardwareUiState {
    data object Loading : HardwareUiState
    data class Success(val capabilities: HardwareCapabilities) : HardwareUiState
    data class Error(val message: String) : HardwareUiState
}

sealed interface SelfTestEvent {
    data class Success(val message: String) : SelfTestEvent
    data class Failure(val errorCode: String, val message: String) : SelfTestEvent
}

@HiltViewModel
class HardwareInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareRepo: HardwareInfoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HardwareUiState>(HardwareUiState.Loading)
    val uiState: StateFlow<HardwareUiState> = _uiState.asStateFlow()

    private val _selfTestEvents = MutableSharedFlow<SelfTestEvent>()
    val selfTestEvents: SharedFlow<SelfTestEvent> = _selfTestEvents.asSharedFlow()

    init {
        loadHardwareInfo()
    }

    fun loadHardwareInfo() {
        viewModelScope.launch {
            _uiState.value = HardwareUiState.Loading
            try {
                val caps = hardwareRepo.getHardwareCapabilities()
                _uiState.value = HardwareUiState.Success(caps)
            } catch (t: Throwable) {
                _uiState.value = HardwareUiState.Error(t.message ?: "Failed to query hardware")
            }
        }
    }

    fun runGpuSelfTest() {
        viewModelScope.launch {
            HdLogger.i("HardwareInfo", "Menjalankan GPU self-test (FR-HW-05)...")
            val res = withContext(Dispatchers.IO) {
                try {
                    NativeBridge.runGpuSelfTest(context.assets, 0)
                } catch (t: Throwable) {
                    null
                }
            }

            if (res != null && res.passed) {
                val msg = "${res.message} (${res.durationMs}ms)"
                HdLogger.i("HardwareInfo", "Self-test GPU lolos: device=${res.deviceName} fp16=${res.fp16Passed} fallback=${res.fp32FallbackUsed}")
                _selfTestEvents.emit(SelfTestEvent.Success(msg))
            } else {
                val errCode = res?.errorCode?.ifEmpty { "HD-HW-105" } ?: "HD-HW-101"
                val errMsg = res?.message ?: "Vulkan self-test execution failed"
                HdLogger.e("HardwareInfo", "$errCode $errMsg")
                _selfTestEvents.emit(SelfTestEvent.Failure(errCode, errMsg))
            }
        }
    }

    suspend fun exportDiagnostics(context: Context): Uri {
        return LogZipExporter.createDiagnosticZip(context, hardwareRepo)
    }
}
