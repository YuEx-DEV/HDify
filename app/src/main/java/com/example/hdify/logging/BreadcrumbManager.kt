package com.example.hdify.logging

import android.content.Context
import com.example.hdify.engine.NativeBridge
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BreadcrumbManager {
    private const val MAX_BREADCRUMBS = 64
    private val buffer = ArrayDeque<String>(MAX_BREADCRUMBS + 4)
    private val lock = Any()
    private var breadcrumbFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var lastFlushTime = 0L

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        breadcrumbFile = File(dir, "breadcrumbs_latest.txt")
    }

    fun add(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"

        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_BREADCRUMBS) {
                buffer.removeFirst()
            }
        }

        // Forward to native layer ring buffer
        if (NativeBridge.isNativeLoaded()) {
            try {
                NativeBridge.addBreadcrumb(message)
            } catch (_: Throwable) {}
        }

        // Throttle disk writes to at most once per 3 seconds to eliminate I/O thrashing in hot loops
        val now = System.currentTimeMillis()
        if (now - lastFlushTime > 3000L) {
            lastFlushTime = now
            flushToDisk()
        }
    }

    fun flushToDisk() {
        val file = breadcrumbFile ?: return
        val snapshot = synchronized(lock) { buffer.toList() }
        try {
            FileWriter(file, false).use { writer ->
                for (item in snapshot) {
                    writer.write(item)
                    writer.write("\n")
                }
                writer.flush()
            }
        } catch (_: Throwable) {}
    }

    fun getBreadcrumbs(): List<String> = synchronized(lock) { buffer.toList() }
}
