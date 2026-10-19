package com.example.hdify.logging

import android.content.Context
import android.util.Log
import com.example.hdify.engine.NativeBridge
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object HdLogger {
    enum class Level(val charCode: Char, val priority: Int) {
        VERBOSE('V', Log.VERBOSE),
        DEBUG('D', Log.DEBUG),
        INFO('I', Log.INFO),
        WARN('W', Log.WARN),
        ERROR('E', Log.ERROR)
    }

    private const val MAX_LOG_FILES = 5
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB
    private const val MAX_TOTAL_LOGS_BYTES = 25 * 1024 * 1024L // 25 MB

    private var logDir: File? = null
    private var currentLogFile: File? = null
    private var isVerbose: Boolean = false
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context, verbose: Boolean = false) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
        currentLogFile = File(dir, "app_log_0.txt")
        isVerbose = verbose

        // Inform native layer of log file location
        currentLogFile?.let { file ->
            if (NativeBridge.isNativeLoaded()) {
                try {
                    NativeBridge.initNativeLogger(file.absolutePath, verbose)
                } catch (_: Throwable) {}
            }
        }

        cleanupStorageIfNeeded()
    }

    fun setVerbose(verbose: Boolean) {
        isVerbose = verbose
        currentLogFile?.let { file ->
            if (NativeBridge.isNativeLoaded()) {
                try {
                    NativeBridge.initNativeLogger(file.absolutePath, verbose)
                } catch (_: Throwable) {}
            }
        }
    }

    fun v(tag: String, message: String) = log(Level.VERBOSE, tag, null, message)
    fun v(tag: String, context: String? = null, message: String) = log(Level.VERBOSE, tag, context, message)

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, null, message)
    fun d(tag: String, context: String? = null, message: String) = log(Level.DEBUG, tag, context, message)

    fun i(tag: String, message: String) = log(Level.INFO, tag, null, message)
    fun i(tag: String, context: String? = null, message: String) = log(Level.INFO, tag, context, message)

    fun w(tag: String, message: String) = log(Level.WARN, tag, null, message)
    fun w(tag: String, message: String, throwable: Throwable?) =
        log(Level.WARN, tag, null, if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message)
    fun w(tag: String, context: String? = null, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, context, if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message)

    fun e(tag: String, message: String) = log(Level.ERROR, tag, null, message)
    fun e(tag: String, message: String, throwable: Throwable?) =
        log(Level.ERROR, tag, null, if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message)
    fun e(tag: String, errorCode: String?, message: String) =
        log(Level.ERROR, tag, null, if (!errorCode.isNullOrEmpty()) "$errorCode $message" else message)
    fun e(tag: String, context: String? = null, errorCode: String? = null, message: String, throwable: Throwable? = null) {
        val fullMsg = buildString {
            if (!errorCode.isNullOrEmpty()) {
                append(errorCode).append(" ")
            }
            append(message)
            if (throwable != null) {
                append("\n").append(Log.getStackTraceString(throwable))
            }
        }
        log(Level.ERROR, tag, context, fullMsg)
    }

    private fun log(level: Level, tag: String, context: String?, message: String) {
        if (!isVerbose && (level == Level.VERBOSE || level == Level.DEBUG)) {
            return
        }

        val logcatMsg = if (!context.isNullOrEmpty()) "$context $message" else message
        Log.println(level.priority, tag, logcatMsg)

        // Queue writing to file asynchronously
        fileExecutor.execute {
            writeLogLine(level, tag, context, message)
        }
    }

    private fun writeLogLine(level: Level, tag: String, context: String?, message: String) {
        val targetFile = currentLogFile ?: return
        try {
            if (targetFile.exists() && targetFile.length() >= MAX_FILE_SIZE_BYTES) {
                rotateFiles()
            }

            val timestamp = dateFormat.format(Date())
            val line = buildString {
                append(timestamp).append(" ")
                append(level.charCode).append("/").append(tag)
                if (!context.isNullOrEmpty()) {
                    append(" ").append(context)
                }
                append(" ").append(message).append("\n")
            }

            FileWriter(targetFile, true).use { writer ->
                writer.write(line)
                writer.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun rotateFiles() {
        val dir = logDir ?: return
        try {
            val oldest = File(dir, "app_log_${MAX_LOG_FILES - 1}.txt")
            if (oldest.exists()) oldest.delete()

            for (i in MAX_LOG_FILES - 2 downTo 0) {
                val f = File(dir, "app_log_$i.txt")
                if (f.exists()) {
                    val next = File(dir, "app_log_${i + 1}.txt")
                    f.renameTo(next)
                }
            }
            currentLogFile = File(dir, "app_log_0.txt")
            if (NativeBridge.isNativeLoaded()) {
                currentLogFile?.let {
                    NativeBridge.initNativeLogger(it.absolutePath, isVerbose)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun cleanupStorageIfNeeded() {
        fileExecutor.execute {
            val dir = logDir ?: return@execute
            try {
                val files = dir.listFiles() ?: return@execute
                var totalSize = files.sumOf { it.length() }
                if (totalSize > MAX_TOTAL_LOGS_BYTES) {
                    val sorted = files.sortedBy { it.lastModified() }
                    for (file in sorted) {
                        if (totalSize <= MAX_TOTAL_LOGS_BYTES / 2) break
                        val sz = file.length()
                        if (file.name != "app_log_0.txt" && file.delete()) {
                            totalSize -= sz
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun getAllLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { _, name -> name.startsWith("app_log_") && name.endsWith(".txt") }
            ?.sortedBy { it.name }
            ?.toList() ?: emptyList()
    }

    fun clearAllLogs() {
        fileExecutor.execute {
            val dir = logDir ?: return@execute
            dir.listFiles { _, name -> name.startsWith("app_log_") }?.forEach { it.delete() }
            currentLogFile = File(dir, "app_log_0.txt")
        }
    }
}
