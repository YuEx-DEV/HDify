package com.example.hdify.ui.queue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import com.example.hdify.domain.usecase.UpscaleImageUseCase
import com.example.hdify.domain.usecase.UpscaleVideoUseCase
import com.example.hdify.service.UpscaleForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val upscaleImageUseCase: UpscaleImageUseCase,
    private val upscaleVideoUseCase: UpscaleVideoUseCase
) : ViewModel() {

    val activeQueue: StateFlow<List<JobEntity>> = jobDao.getActiveQueueFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun cancelJob(job: JobEntity) {
        viewModelScope.launch {
            if (job.status == "PROCESSING") {
                upscaleImageUseCase.cancel()
                upscaleVideoUseCase.cancel()
                UpscaleForegroundService.cancelJob(context, job.id)
            }
            jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))

            try {
                val remainingActive = jobDao.getActiveQueueFlow().first().filter {
                    it.id != job.id && (it.status == "PENDING" || it.status == "PROCESSING")
                }
                if (remainingActive.isEmpty()) {
                    UpscaleForegroundService.dismissOngoingNotification(context)
                }
            } catch (_: Exception) {}
        }
    }
}
