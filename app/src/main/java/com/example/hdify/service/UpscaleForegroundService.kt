package com.example.hdify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.hdify.R
import com.example.hdify.data.database.JobDao
import com.example.hdify.data.database.JobEntity
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.usecase.ImageUpscaleStatus
import com.example.hdify.domain.usecase.UpscaleImageUseCase
import com.example.hdify.domain.usecase.UpscaleRequest
import com.example.hdify.domain.usecase.UpscaleVideoUseCase
import com.example.hdify.domain.usecase.VideoUpscaleRequest
import com.example.hdify.domain.usecase.VideoUpscaleStatus
import com.example.hdify.logging.HdLogger
import com.example.hdify.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpscaleForegroundService : Service() {

    object ServiceControlRegistry {
        @Volatile
        var onCancelDirect: (() -> Unit)? = null

        @Volatile
        var onPauseResumeDirect: ((Boolean) -> Unit)? = null
    }

    @Inject
    lateinit var jobDao: JobDao

    @Inject
    lateinit var upscaleUseCase: UpscaleImageUseCase

    @Inject
    lateinit var upscaleVideoUseCase: UpscaleVideoUseCase

    @Inject
    lateinit var thermalBatteryMonitor: ThermalBatteryMonitor

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processingJob: Job? = null
    private var isCancelled = false
    private var isPaused = false
    @Volatile
    private var currentJobCancelled = false
    private var isDirectMode = false
    private var directIsVideo = false
    private var directMediaType = "IMAGE"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        thermalBatteryMonitor.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_DIRECT -> {
                isServiceActive = true
                currentJobCancelled = false
                isDirectMode = true
                isCancelled = false
                isPaused = false
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "IMAGE"
                directMediaType = mediaType
                directIsVideo = mediaType.equals("VIDEO", ignoreCase = true) || mediaType.equals("COMPRESS", ignoreCase = true)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.notif_preparing)

                acquireWakeLock()
                val notification = buildOngoingNotification(
                    context = this,
                    title = title,
                    message = message,
                    progress = 0,
                    mediaType = directMediaType,
                    isPaused = false
                )
                startInForeground(notification)
                HdLogger.i(TAG, "Direct foreground processing started: $mediaType")
                return START_STICKY
            }

            ACTION_FINISH_DIRECT -> {
                isServiceActive = false
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, true)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.notif_completed_title)
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val outputUriStr = intent.getStringExtra(EXTRA_OUTPUT_URI)

                dismissOngoingNotification(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()

                if (success) {
                    showCompletionNotification(this, title, message, outputUriStr)
                } else {
                    showFailureNotification(this, title, message)
                }
                stopSelf()
                HdLogger.i(TAG, "Direct foreground processing finished (success=$success)")
                return START_NOT_STICKY
            }

            ACTION_STOP_DIRECT -> {
                isServiceActive = false
                isCancelled = true
                currentJobCancelled = true
                upscaleUseCase.cancel()
                upscaleVideoUseCase.cancel()
                ServiceControlRegistry.onCancelDirect?.invoke()
                dismissOngoingNotification(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf()
                HdLogger.i(TAG, "Direct foreground processing stopped/aborted")
                return START_NOT_STICKY
            }

            ACTION_CANCEL -> {
                val targetJobId = intent.getLongExtra(EXTRA_JOB_ID, 0L)
                if (targetJobId == 0L || isDirectMode) {
                    currentJobCancelled = true
                    isServiceActive = false
                    isCancelled = true
                    upscaleUseCase.cancel()
                    upscaleVideoUseCase.cancel()
                    ServiceControlRegistry.onCancelDirect?.invoke()
                    dismissOngoingNotification(this)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    releaseWakeLock()
                    stopSelf()

                    val activeId = if (targetJobId != 0L) targetJobId else currentActiveJobId
                    if (activeId != 0L) {
                        currentActiveJobId = 0L
                        serviceScope.launch {
                            try {
                                val curr = jobDao.getJobById(activeId)
                                if (curr != null && (curr.status == "PENDING" || curr.status == "PROCESSING")) {
                                    jobDao.updateJob(curr.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    HdLogger.i(TAG, "Cancel requested for direct active rendering or entire queue")
                } else {
                    HdLogger.i(TAG, "Cancel requested for queued job #$targetJobId (current active is #$currentActiveJobId)")
                    serviceScope.launch {
                        val target = jobDao.getJobById(targetJobId)
                        if (target != null && (target.status == "PENDING" || target.status == "PROCESSING")) {
                            jobDao.updateJob(target.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                        }
                        val remainingActive = jobDao.getActiveQueueFlow().first().filter {
                            it.id != targetJobId && (it.status == "PENDING" || it.status == "PROCESSING")
                        }
                        if (remainingActive.isEmpty()) {
                            HdLogger.i(TAG, "No more active jobs in queue after cancelling #$targetJobId, stopping service and dismissing notification")
                            isCancelled = true
                            isServiceActive = false
                            dismissOngoingNotification(this@UpscaleForegroundService)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            releaseWakeLock()
                            stopSelf()
                        }
                    }
                    if (targetJobId == currentActiveJobId) {
                        currentJobCancelled = true
                        upscaleUseCase.cancel()
                        upscaleVideoUseCase.cancel()
                    }
                }
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                isPaused = true
                upscaleVideoUseCase.pause()
                ServiceControlRegistry.onPauseResumeDirect?.invoke(true)
                val notification = buildOngoingNotification(
                    context = this,
                    title = getString(R.string.notif_paused_title),
                    message = "Proses sedang dijeda",
                    progress = 0,
                    mediaType = directMediaType,
                    isPaused = true
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                HdLogger.i(TAG, "Pause requested for active render")
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                isPaused = false
                upscaleVideoUseCase.resume()
                ServiceControlRegistry.onPauseResumeDirect?.invoke(false)
                val resumeTitle = if (directMediaType.equals("COMPRESS", ignoreCase = true)) {
                    getString(R.string.notif_compress_title)
                } else {
                    getString(R.string.notif_video_processing_title, 2)
                }
                val notification = buildOngoingNotification(
                    context = this,
                    title = resumeTitle,
                    message = "Melanjutkan proses...",
                    progress = 0,
                    mediaType = directMediaType,
                    isPaused = false
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                HdLogger.i(TAG, "Resume requested for active render")
                return START_NOT_STICKY
            }

            ACTION_START -> {
                isServiceActive = true
                isDirectMode = false
                isCancelled = false
                currentJobCancelled = false
                acquireWakeLock()
                val notification = buildOngoingNotification(
                    context = this,
                    title = getString(R.string.app_name),
                    message = "Menjalankan antrian pemrosesan di latar belakang...",
                    progress = 0,
                    mediaType = "IMAGE",
                    isPaused = false
                )
                startInForeground(notification)

                if (processingJob == null || processingJob?.isActive != true) {
                    processingJob = serviceScope.launch {
                        processQueue()
                    }
                }
                return START_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            HdLogger.e(TAG, "Gagal memulai service di foreground: ${e.message}", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HDify:ProcessingWakeLock")?.apply {
                acquire(60 * 60 * 1000L) // 60 min safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private suspend fun processQueue() {
        val processedJobIds = mutableSetOf<Long>()
        var successCount = 0
        var failedCount = 0
        try {
            while (!isCancelled) {
                val activeQueue = jobDao.getActiveQueueFlow().first()
                val nextJob = activeQueue.firstOrNull { it.status == "PENDING" && it.id !in processedJobIds }

                if (nextJob == null) {
                    HdLogger.i(TAG, "Queue empty, stopping foreground service")
                    break
                }

                currentActiveJobId = nextJob.id
                processedJobIds.add(nextJob.id)
                try {
                    val ok = processSingleJob(nextJob)
                    if (ok) successCount++ else failedCount++
                } catch (e: Exception) {
                    failedCount++
                    HdLogger.e(TAG, "Error executing job #${nextJob.id}: ${e.message}", e)
                    try {
                        jobDao.updateJob(nextJob.copy(
                            status = "FAILED",
                            errorMessage = e.message ?: "Job processing error",
                            completedAt = System.currentTimeMillis()
                        ))
                    } catch (_: Exception) {}
                    showFailureNotification(
                        context = this,
                        title = getString(R.string.notif_job_failed_title, nextJob.id),
                        message = e.message ?: getString(R.string.error_generic)
                    )
                }

                if (isCancelled) {
                    HdLogger.i(TAG, "Queue processing halted due to cancellation")
                    break
                }
            }

            if ((successCount + failedCount) > 1 && !isCancelled) {
                showCompletionNotification(
                    context = this,
                    title = getString(R.string.notif_queue_completed_title),
                    message = getString(R.string.notif_queue_completed_msg, successCount, failedCount),
                    outputUriStr = null
                )
            }
        } finally {
            isServiceActive = false
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            dismissOngoingNotification(this)
            stopSelf()
        }
    }

    private suspend fun processSingleJob(job: JobEntity): Boolean {
        val jobStartTime = System.currentTimeMillis()
        HdLogger.i(TAG, "Processing queued job #${job.id}: ${job.sourceUri} scale x${job.scale}")

        jobDao.updateJob(job.copy(status = "PROCESSING", progress = 0))

        val engineType = if (job.engineName.contains("ESRGAN", ignoreCase = true)) {
            EngineType.REAL_ESRGAN
        } else {
            EngineType.REAL_CUGAN
        }

        var lastUpdateMs = 0L

        if (job.mediaType == "VIDEO") {
            val isHevc = job.engineName.contains("HEVC", ignoreCase = true)
            val model = if (engineType == EngineType.REAL_ESRGAN) {
                "realesr-animevideov3-x${job.scale}"
            } else {
                "models-se"
            }

            val videoReq = VideoUpscaleRequest(
                sourceUri = Uri.parse(job.sourceUri),
                scale = job.scale,
                engineType = engineType,
                modelDir = model,
                isHevc = isHevc,
                qualityPercent = 85,
                isPreview3s = false,
                existingJobId = job.id
            )

            var lastVideoDbUpdateMs = 0L
            val result = upscaleVideoUseCase.execute(videoReq) { progress ->
                if (currentJobCancelled || !isServiceActive) return@execute
                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 350) {
                    lastUpdateMs = now
                    val etaText = if (progress.etaSeconds > 0) " • ETA ${progress.etaSeconds}s (%.1f FPS)".format(progress.fps) else ""
                    updateProgress(
                        context = this,
                        progress = progress.percent,
                        title = "Memproses Video #${job.id} (x${job.scale})",
                        message = "Frame ${progress.currentFrame}/${progress.totalFrames} (${progress.percent}%)$etaText",
                        mediaType = "VIDEO",
                        isPaused = isPaused
                    )
                }

                if (now - lastVideoDbUpdateMs > 250) {
                    lastVideoDbUpdateMs = now
                    serviceScope.launch {
                        try {
                            val current = jobDao.getJobById(job.id)
                            if (current != null && current.status == "PROCESSING") {
                                jobDao.updateJob(current.copy(
                                    progress = progress.percent,
                                    checkpointFrame = progress.currentFrame,
                                    totalFrames = progress.totalFrames
                                ))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val latestVideoJob = jobDao.getJobById(job.id)
            if (latestVideoJob?.status == "CANCELLED") {
                HdLogger.w(TAG, "Video Job #${job.id} was cancelled during processing")
                return false
            }

            return when (result) {
                is VideoUpscaleStatus.Success -> {
                    jobDao.updateJob(job.copy(status = "COMPLETED", progress = 100, outputUri = result.outputUri.toString(), completedAt = System.currentTimeMillis()))
                    HdLogger.i(TAG, "Video Job #${job.id} completed successfully in ${result.durationMs}ms")
                    showCompletionNotification(
                        context = this,
                        title = getString(R.string.notif_completed_title),
                        message = getString(R.string.notif_completed_video_msg),
                        outputUriStr = result.outputUri.toString()
                    )
                    true
                }
                is VideoUpscaleStatus.Canceled -> {
                    jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                    HdLogger.w(TAG, "Video Job #${job.id} was canceled")
                    false
                }
                is VideoUpscaleStatus.Error -> {
                    jobDao.updateJob(job.copy(status = "FAILED", errorMessage = "${result.code}: ${result.message}", completedAt = System.currentTimeMillis()))
                    HdLogger.e(TAG, "Video Job #${job.id} failed with ${result.code}: ${result.message}")
                    showFailureNotification(
                        context = this,
                        title = getString(R.string.notif_job_failed_title, job.id),
                        message = "${result.code}: ${result.message}"
                    )
                    false
                }
                is VideoUpscaleStatus.Progress -> false
            }

        } else {
            // IMAGE job
            val request = UpscaleRequest(
                sourceUri = Uri.parse(job.sourceUri),
                scale = job.scale,
                engineType = engineType,
                modelDir = if (engineType == EngineType.REAL_CUGAN) "models-se" else "realesrgan-x4plus",
                denoise = 0,
                tileSize = 0,
                useFp16 = true,
                isCropPreview = false,
                existingJobId = job.id
            )

            var lastDbUpdateMs = 0L

            val result = upscaleUseCase.execute(request) { progress ->
                if (currentJobCancelled || !isServiceActive) return@execute
                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 350) {
                    lastUpdateMs = now
                    val elapsed = (now - jobStartTime).coerceAtLeast(1)
                    val etaMs = if (progress.percent > 0) {
                        ((elapsed * (100 - progress.percent)) / progress.percent)
                    } else 0L

                    val etaSec = (etaMs / 1000).toInt()
                    val etaText = if (etaSec > 0) " • ETA ${etaSec}s" else ""

                    updateProgress(
                        context = this,
                        progress = progress.percent,
                        title = "Memproses Gambar #${job.id} (x${job.scale})",
                        message = "Tile ${progress.current}/${progress.total} (${progress.percent}%)$etaText",
                        mediaType = "IMAGE",
                        isPaused = false
                    )
                }

                if (now - lastDbUpdateMs > 250) {
                    lastDbUpdateMs = now
                    serviceScope.launch {
                        try {
                            val current = jobDao.getJobById(job.id)
                            if (current != null && current.status == "PROCESSING") {
                                jobDao.updateJob(current.copy(progress = progress.percent))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val latestImageJob = jobDao.getJobById(job.id)
            if (latestImageJob?.status == "CANCELLED") {
                HdLogger.w(TAG, "Image Job #${job.id} was cancelled during processing")
                return false
            }

            return when (result) {
                is ImageUpscaleStatus.Success -> {
                    jobDao.updateJob(job.copy(status = "COMPLETED", progress = 100, outputUri = result.outputUri.toString(), completedAt = System.currentTimeMillis()))
                    HdLogger.i(TAG, "Job #${job.id} completed successfully in ${result.durationMs}ms")
                    showCompletionNotification(
                        context = this,
                        title = getString(R.string.notif_completed_title),
                        message = getString(R.string.notif_completed_image_msg, result.width, result.height),
                        outputUriStr = result.outputUri.toString()
                    )
                    true
                }
                is ImageUpscaleStatus.Canceled -> {
                    jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                    HdLogger.w(TAG, "Job #${job.id} was canceled")
                    false
                }
                is ImageUpscaleStatus.Error -> {
                    jobDao.updateJob(job.copy(status = "FAILED", errorMessage = "${result.code}: ${result.message}", completedAt = System.currentTimeMillis()))
                    HdLogger.e(TAG, "Job #${job.id} failed with ${result.code}: ${result.message}")
                    showFailureNotification(
                        context = this,
                        title = getString(R.string.notif_job_failed_title, job.id),
                        message = "${result.code}: ${result.message}"
                    )
                    false
                }
                is ImageUpscaleStatus.Progress -> false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        dismissOngoingNotification(this)
        thermalBatteryMonitor.stopMonitoring()
        releaseWakeLock()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "UpscaleService"
        const val CHANNEL_ID = "hdify_upscale_channel"
        const val CHANNEL_COMPLETED_ID = "hdify_completed_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceActive: Boolean = false
            internal set

        @Volatile
        var currentActiveJobId: Long = 0L

        fun dismissOngoingNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                HdLogger.w(TAG, "Failed to dismiss ongoing notification: ${e.message}")
            }
        }

        const val ACTION_START = "com.example.hdify.action.START_QUEUE"
        const val ACTION_START_DIRECT = "com.example.hdify.action.START_DIRECT"
        const val ACTION_FINISH_DIRECT = "com.example.hdify.action.FINISH_DIRECT"
        const val ACTION_STOP_DIRECT = "com.example.hdify.action.STOP_DIRECT"
        const val ACTION_CANCEL = "com.example.hdify.action.CANCEL_JOB"
        const val ACTION_PAUSE = "com.example.hdify.action.PAUSE_JOB"
        const val ACTION_RESUME = "com.example.hdify.action.RESUME_JOB"

        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_OUTPUT_URI = "extra_output_uri"

        private var lastUpdateTimestamp = 0L

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val ongoingChannel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_processing_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_processing_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(ongoingChannel)

                val completedChannel = NotificationChannel(
                    CHANNEL_COMPLETED_ID,
                    context.getString(R.string.notif_channel_completed_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_completed_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(completedChannel)
            }
        }

        fun buildOngoingNotification(
            context: Context,
            title: String,
            message: String,
            progress: Int,
            mediaType: String = "IMAGE",
            isPaused: Boolean = false
        ): Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, UpscaleForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val iconRes = when (mediaType.uppercase()) {
                "VIDEO" -> R.drawable.ic_video
                "COMPRESS" -> R.drawable.ic_compress
                "DOWNLOAD" -> R.drawable.ic_download
                "BG_REMOVER" -> R.drawable.ic_auto_awesome
                else -> R.drawable.ic_image
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(iconRes)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress <= 0)

            val supportsPause = mediaType.equals("VIDEO", ignoreCase = true) || mediaType.equals("COMPRESS", ignoreCase = true)
            if (supportsPause) {
                val pauseResumeIntent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
                }
                val pauseResumePendingIntent = PendingIntent.getService(
                    context,
                    2,
                    pauseResumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val pauseResumeTitle = if (isPaused) context.getString(R.string.btn_resume) else context.getString(R.string.btn_pause)
                val pauseResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                builder.addAction(pauseResumeIcon, pauseResumeTitle, pauseResumePendingIntent)
            }

            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.btn_cancel), cancelPendingIntent)

            return builder.build()
        }

        fun buildOngoingNotification(
            context: Context,
            title: String,
            message: String,
            progress: Int,
            isVideo: Boolean,
            isPaused: Boolean
        ): Notification = buildOngoingNotification(
            context = context,
            title = title,
            message = message,
            progress = progress,
            mediaType = if (isVideo) "VIDEO" else "IMAGE",
            isPaused = isPaused
        )

        fun startDirect(context: Context, mediaType: String, title: String, message: String) {
            isServiceActive = true
            lastUpdateTimestamp = 0L
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_START_DIRECT
                    putExtra(EXTRA_MEDIA_TYPE, mediaType)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan startDirect: ${e.message}", e)
            }
        }

        fun updateProgress(
            context: Context,
            progress: Int,
            title: String,
            message: String,
            mediaType: String = "IMAGE",
            isPaused: Boolean = false
        ) {
            if (!isServiceActive) return
            val now = System.currentTimeMillis()
            if (now - lastUpdateTimestamp < 350) return
            lastUpdateTimestamp = now

            try {
                if (!isServiceActive) return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val notification = buildOngoingNotification(context, title, message, progress, mediaType, isPaused)
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                HdLogger.w(TAG, "Failed to update notification: ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            progress: Int,
            title: String,
            message: String,
            isVideo: Boolean,
            isPaused: Boolean = false
        ) = updateProgress(context, progress, title, message, if (isVideo) "VIDEO" else "IMAGE", isPaused)

        fun finishDirect(
            context: Context,
            success: Boolean,
            title: String,
            message: String,
            outputUri: Uri? = null
        ) {
            isServiceActive = false
            lastUpdateTimestamp = 0L
            dismissOngoingNotification(context)
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_FINISH_DIRECT
                    putExtra(EXTRA_SUCCESS, success)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                    outputUri?.let { putExtra(EXTRA_OUTPUT_URI, it.toString()) }
                }
                context.startService(intent)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan finishDirect: ${e.message}", e)
            }
        }

        fun stopDirect(context: Context) {
            isServiceActive = false
            lastUpdateTimestamp = 0L
            dismissOngoingNotification(context)
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_STOP_DIRECT
                }
                context.startService(intent)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan stopDirect: ${e.message}", e)
            }
        }

        fun showCompletionNotification(
            context: Context,
            title: String,
            message: String,
            outputUriStr: String? = null,
            iconRes: Int = R.drawable.ic_check_circle
        ) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (!outputUriStr.isNullOrEmpty()) {
                        putExtra("outputUri", outputUriStr)
                    }
                }
                val notifId = (System.currentTimeMillis() % 100000).toInt() + 2000
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETED_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(iconRes)
                    .setContentIntent(openPendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)

                if (!outputUriStr.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse(outputUriStr)
                        val mimeType = when {
                            outputUriStr.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                            outputUriStr.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                            outputUriStr.endsWith(".jpg", ignoreCase = true) || outputUriStr.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                            outputUriStr.endsWith(".png", ignoreCase = true) -> "image/png"
                            outputUriStr.endsWith(".webp", ignoreCase = true) -> "image/webp"
                            else -> context.contentResolver.getType(uri) ?: "*/*"
                        }
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val viewPendingIntent = PendingIntent.getActivity(
                            context,
                            notifId + 10000,
                            viewIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(
                            R.drawable.ic_visibility,
                            context.getString(R.string.notif_action_open_file),
                            viewPendingIntent
                        )
                    } catch (_: Exception) {}
                }

                manager.notify(notifId, builder.build())
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menampilkan notifikasi selesai: ${e.message}", e)
            }
        }

        fun showFailureNotification(
            context: Context,
            title: String,
            message: String
        ) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val notifId = (System.currentTimeMillis() % 100000).toInt() + 2000
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notif = NotificationCompat.Builder(context, CHANNEL_COMPLETED_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_warning)
                    .setContentIntent(openPendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build()

                manager.notify(notifId, notif)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menampilkan notifikasi kegagalan: ${e.message}", e)
            }
        }

        fun start(context: Context) {
            isServiceActive = true
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan start: ${e.message}", e)
            }
        }

        fun pauseJob(context: Context, jobId: Long) {
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_PAUSE
                    putExtra(EXTRA_JOB_ID, jobId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan pauseJob: ${e.message}", e)
            }
        }

        fun resumeJob(context: Context, jobId: Long) {
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_RESUME
                    putExtra(EXTRA_JOB_ID, jobId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan resumeJob: ${e.message}", e)
            }
        }

        fun cancelJob(context: Context, jobId: Long) {
            try {
                val intent = Intent(context, UpscaleForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_JOB_ID, jobId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                HdLogger.e(TAG, "Gagal menjalankan cancelJob: ${e.message}", e)
            }
        }
    }
}
