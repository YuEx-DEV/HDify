package com.example.hdify.ui.logs

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.hardware.HardwareInfoRepository
import com.example.hdify.logging.CrashHandler
import com.example.hdify.logging.HdLogger
import com.example.hdify.logging.LogZipExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class LogFilter {
    ALL,
    INFO,
    WARN,
    ERROR,
    CRASHES_ONLY
}

@HiltViewModel
class LogCrashReportViewModel @Inject constructor(
    private val hardwareRepo: HardwareInfoRepository
) : ViewModel() {

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _currentFilter = MutableStateFlow(LogFilter.ALL)
    val currentFilter: StateFlow<LogFilter> = _currentFilter.asStateFlow()

    private var allEntriesCache = listOf<LogEntry>()

    init {
        loadLogs()
    }

    fun setFilter(filter: LogFilter) {
        _currentFilter.value = filter
        applyFilter()
    }

    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<LogEntry>()

            // 1. Read Crash Files
            val crashFiles = CrashHandler.getCrashFiles()
            for (file in crashFiles) {
                try {
                    val lines = file.readLines()
                    for (line in lines) {
                        list.add(
                            LogEntry(
                                id = UUID.randomUUID().toString(),
                                line = line,
                                levelChar = 'C',
                                isCrash = true
                            )
                        )
                    }
                } catch (_: Throwable) {}
            }

            // 2. Read App Log Files
            val logFiles = HdLogger.getAllLogFiles()
            for (file in logFiles) {
                try {
                    val lines = file.readLines()
                    for (line in lines) {
                        val levelChar = extractLevelChar(line)
                        list.add(
                            LogEntry(
                                id = UUID.randomUUID().toString(),
                                line = line,
                                levelChar = levelChar,
                                isCrash = false
                            )
                        )
                    }
                } catch (_: Throwable) {}
            }

            allEntriesCache = list
            withContext(Dispatchers.Main) {
                applyFilter()
            }
        }
    }

    private fun extractLevelChar(line: String): Char {
        val parts = line.split(" ")
        for (part in parts) {
            if (part.length >= 2 && part[1] == '/') {
                return part[0]
            }
        }
        return 'I'
    }

    private fun applyFilter() {
        val filter = _currentFilter.value
        _logEntries.value = when (filter) {
            LogFilter.ALL -> allEntriesCache
            LogFilter.INFO -> allEntriesCache.filter { it.levelChar in listOf('I', 'W', 'E', 'C') }
            LogFilter.WARN -> allEntriesCache.filter { it.levelChar in listOf('W', 'E', 'C') }
            LogFilter.ERROR -> allEntriesCache.filter { it.levelChar in listOf('E', 'C') }
            LogFilter.CRASHES_ONLY -> allEntriesCache.filter { it.isCrash }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            HdLogger.clearAllLogs()
            CrashHandler.clearAllCrashes()
            allEntriesCache = emptyList()
            withContext(Dispatchers.Main) {
                _logEntries.value = emptyList()
            }
        }
    }

    fun getFullLogText(): String {
        return _logEntries.value.joinToString("\n") { it.line }
    }

    suspend fun exportDiagnostics(context: Context): Uri {
        return LogZipExporter.createDiagnosticZip(context, hardwareRepo)
    }

    fun triggerKotlinCrash() {
        CrashHandler.triggerKotlinCrashForTesting()
    }

    fun triggerNativeCrash() {
        CrashHandler.triggerNativeCrashForTesting()
    }
}
