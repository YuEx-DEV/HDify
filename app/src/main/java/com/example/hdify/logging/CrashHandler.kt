package com.example.hdify.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.hdify.BuildConfig
import com.example.hdify.engine.NativeBridge
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var crashDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = File(context.filesDir, "crashes")
        if (!dir.exists()) dir.mkdirs()
        crashDir = dir

        // Install Kotlin uncaught exception handler
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Install native crash handler via JNI
        if (NativeBridge.isNativeLoaded()) {
            try {
                NativeBridge.installCrashHandler(dir.absolutePath)
                HdLogger.i("CrashHandler", null, "Native crash handler registered at: ${dir.absolutePath}")
            } catch (t: Throwable) {
                HdLogger.e("CrashHandler", null, "HD-SYS-999", "Failed to register native crash handler", t)
            }
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val dir = crashDir ?: return
        try {
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(dir, "crash_kotlin_${timestampStr}.txt")

            FileWriter(crashFile, false).use { writer ->
                writer.write("=== HDIFY KOTLIN CRASH REPORT ===\n")
                writer.write("Timestamp: ${dateFormat.format(Date())}\n")
                writer.write("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                writer.write("Build Type: ${BuildConfig.BUILD_TYPE}\n")
                writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
                writer.write("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                writer.write("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
                writer.write("Crashing Thread: ${thread.name} (id=${thread.id})\n")
                writer.write("Exception: ${throwable.javaClass.name}: ${throwable.message}\n\n")

                writer.write("--- STACK TRACE ---\n")
                writer.write(Log.getStackTraceString(throwable))
                writer.write("\n\n")

                writer.write("--- BREADCRUMBS ---\n")
                val crumbs = BreadcrumbManager.getBreadcrumbs()
                for (crumb in crumbs) {
                    writer.write(crumb)
                    writer.write("\n")
                }
                writer.write("=== END CRASH REPORT ===\n")
                writer.flush()
            }
        } catch (_: Throwable) {}
    }

    fun getCrashFiles(): List<File> {
        val dir = crashDir ?: return emptyList()
        return dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.toList() ?: emptyList()
    }

    fun clearAllCrashes() {
        val dir = crashDir ?: return
        dir.listFiles { _, name -> name.startsWith("crash_") }?.forEach { it.delete() }
    }

    fun triggerKotlinCrashForTesting() {
        throw RuntimeException("Uji Crash Kotlin sengaja dipicu untuk verifikasi AC-LOG-1")
    }

    fun triggerNativeCrashForTesting() {
        if (NativeBridge.isNativeLoaded()) {
            NativeBridge.triggerNativeCrash()
        }
    }
}
