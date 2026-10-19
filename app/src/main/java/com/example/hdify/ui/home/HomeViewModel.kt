package com.example.hdify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.hardware.HardwareInfoRepository
import com.example.hdify.domain.model.HardwareCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val capabilities: HardwareCapabilities) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hardwareRepo: HardwareInfoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHardwareCapabilities()
    }

    fun loadHardwareCapabilities() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val caps = hardwareRepo.getHardwareCapabilities()
                _uiState.value = HomeUiState.Success(caps)
            } catch (t: Throwable) {
                _uiState.value = HomeUiState.Error(t.message ?: "Unknown hardware query error")
            }
        }
    }
}
