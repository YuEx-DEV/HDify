package com.example.hdify.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

data class CrashIncident(
    val hasIncident: Boolean,
    val title: String,
    val description: String,
    val latestCrashFile: File? = null,
    val exitReasonDetail: String? = null,
    val exitTimestamp: Long? = null
)

object StartupCrashDetector {
    private const val MARKER_FILE = "session_active.marker"
    private const val LAST_ACK_FILE = "last_seen_crash.marker"

    fun checkForPreviousCrash(context: Context): CrashIncident {
        val marker = File(context.filesDir, MARKER_FILE)
        val ackFile = File(context.filesDir, LAST_ACK_FILE)
        val lastAckTimestamp = if (ackFile.exists()) ackFile.readText().toLongOrNull() ?: 0L else 0L

        val crashFiles = CrashHandler.getCrashFiles()
        val latestCrash = crashFiles.firstOrNull()

        var hasAbnormalExit = false
        var reasonDescription = ""
        var exitDetail: String? = null
        var exitTimestamp: Long? = null

        // 1. Check Historical Process Exit Reasons on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val reasons = am?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                val lastExit = reasons?.firstOrNull()
                if (lastExit != null && lastExit.timestamp > lastAckTimestamp) {
                    when (lastExit.reason) {
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                            hasAbnormalExit = true
                            reasonDescription = "Crash Native (Signal / SIGSEGV)"
                            exitDetail = "Status=${lastExit.status}, Description=${lastExit.description}"
                            exitTimestamp = lastExit.timestamp
                        }
                        ApplicationExitInfo.REASON_CRASH -> {
                            hasAbnormalExit = true
                            reasonDescription = "Exception Java/Kotlin tidak tertangani"
                            exitDetail = "Status=${lastExit.status}, Description=${lastExit.description}"
                            exitTimestamp = lastExit.timestamp
                        }
                        ApplicationExitInfo.REASON_ANR -> {
                            hasAbnormalExit = true
                            reasonDescription = "Aplikasi Tidak Merespon (ANR)"
                            exitDetail = "Description=${lastExit.description}"
                            exitTimestamp = lastExit.timestamp
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. Check Crash Files (UncaughtExceptionHandler and native signal handler logs)
        val isNewCrashFile = latestCrash != null && latestCrash.lastModified() > lastAckTimestamp

        if (isNewCrashFile) {
            hasAbnormalExit = true
            exitTimestamp = maxOf(exitTimestamp ?: 0L, latestCrash!!.lastModified())
            if (reasonDescription.isEmpty()) {
                reasonDescription = if (latestCrash.name.contains("native")) {
                    "Crash Native (SIGSEGV / Sinyal Native)"
                } else {
                    "Exception Kotlin/Java tidak tertangani"
                }
            }
        }

        // Clean up legacy marker file if present
        if (marker.exists()) {
            try { marker.delete() } catch (_: Throwable) {}
        }

        return if (hasAbnormalExit) {
            CrashIncident(
                hasIncident = true,
                title = "Aplikasi Berhenti Tak Terduga",
                description = reasonDescription,
                latestCrashFile = latestCrash,
                exitReasonDetail = exitDetail,
                exitTimestamp = exitTimestamp
            )
        } else {
            CrashIncident(
                hasIncident = false,
                title = "",
                description = ""
            )
        }
    }

    fun markSessionActive(context: Context) {
        try {
            val marker = File(context.filesDir, MARKER_FILE)
            marker.writeText("${System.currentTimeMillis()}")
        } catch (_: Throwable) {}
    }

    fun markSessionCleanExit(context: Context) {
        try {
            val marker = File(context.filesDir, MARKER_FILE)
            if (marker.exists()) marker.delete()
        } catch (_: Throwable) {}
    }

    fun acknowledgeIncident(context: Context, incident: CrashIncident) {
        try {
            val ackFile = File(context.filesDir, LAST_ACK_FILE)
            val timestamp = maxOf(
                incident.latestCrashFile?.lastModified() ?: 0L,
                incident.exitTimestamp ?: 0L,
                System.currentTimeMillis()
            )
            ackFile.writeText("$timestamp")

            val marker = File(context.filesDir, MARKER_FILE)
            if (marker.exists()) marker.delete()
        } catch (_: Throwable) {}
    }
}
