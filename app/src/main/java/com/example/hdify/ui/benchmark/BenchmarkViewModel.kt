package com.example.hdify.ui.benchmark

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.domain.model.BenchmarkResult
import com.example.hdify.engine.NativeBridge
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()

    fun runBenchmark() {
        if (_isRunning.value) return

        viewModelScope.launch {
            _isRunning.value = true
            _benchmarkResults.value = emptyList()

            val results = mutableListOf<BenchmarkResult>()

            withContext(Dispatchers.IO) {
                // Test 1: Real-CUGAN 2x on 256x256 tile
                _statusText.value = "Menjalankan benchmark Real-CUGAN x2 (tile 256×256)..."
                HdLogger.i(TAG, "Starting Real-CUGAN 2x benchmark")
                try {
                    val cuganRes = NativeBridge.runBenchmark(context.assets, 0, 2, 0)
                    results.add(cuganRes)
                    _benchmarkResults.value = results.toList()
                } catch (e: Exception) {
                    HdLogger.e(TAG, "Real-CUGAN benchmark error: ${e.message}", e)
                }

                // Test 2: Real-ESRGAN 4x on 256x256 tile
                _statusText.value = "Menjalankan benchmark Real-ESRGAN x4 (tile 256×256)..."
                HdLogger.i(TAG, "Starting Real-ESRGAN 4x benchmark")
                try {
                    val esrganRes = NativeBridge.runBenchmark(context.assets, 1, 4, 0)
                    results.add(esrganRes)
                    _benchmarkResults.value = results.toList()
                } catch (e: Exception) {
                    HdLogger.e(TAG, "Real-ESRGAN benchmark error: ${e.message}", e)
                }
            }

            _statusText.value = "Benchmark selesai"
            _isRunning.value = false
        }
    }

    companion object {
        private const val TAG = "BenchmarkVM"
    }
}
