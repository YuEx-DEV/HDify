package com.example.hdify.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val jobDao: JobDao
) : ViewModel() {

    val historyJobs: StateFlow<List<JobEntity>> = jobDao.getHistoryFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearHistory() {
        viewModelScope.launch {
            jobDao.clearHistoryOnly()
        }
    }

    fun deleteJob(jobId: Long) {
        viewModelScope.launch {
            jobDao.deleteJobById(jobId)
        }
    }
}
