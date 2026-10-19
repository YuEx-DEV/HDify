package com.example.hdify.logging

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.hdify.BuildConfig
import com.example.hdify.data.hardware.HardwareInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogZipExporter {

    suspend fun createDiagnosticZip(
        context: Context,
        hardwareRepo: HardwareInfoRepository
    ): Uri = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(exportDir, "hdify_diagnostic_$timestamp.zip")

        val caps = hardwareRepo.getHardwareCapabilities()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Add system_info.txt
            val sysInfoContent = buildString {
                append("=== HDIFY SYSTEM & DIAGNOSTIC REPORT ===\n")
                append("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("Build Type: ${BuildConfig.BUILD_TYPE}\n\n")

                append("--- DEVICE INFO ---\n")
                append("Manufacturer: ${Build.MANUFACTURER}\n")
                append("Model: ${Build.MODEL}\n")
                append("Device: ${Build.DEVICE}\n")
                append("Hardware: ${Build.HARDWARE}\n")
                append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n\n")

                append("--- GPU & VULKAN INFO (ncnn) ---\n")
                append("Vulkan Available: ${caps.gpuInfo.vulkanAvailable}\n")
                append("GPU Count: ${caps.gpuInfo.gpuCount}\n")
                if (caps.gpuInfo.vulkanAvailable) {
                    append("GPU Name: ${caps.gpuInfo.deviceName}\n")
                    append("GPU Type: ${caps.gpuInfo.deviceType}\n")
                    append("Vulkan API Version: ${caps.gpuInfo.apiVersion}\n")
                    append("Driver Version: ${caps.gpuInfo.driverVersion}\n")
                    val heapMb = caps.gpuInfo.memoryHeapBytes / (1024 * 1024)
                    append("Memory Heap: $heapMb MB (${caps.gpuInfo.memoryHeapBytes} bytes)\n")
                    append("FP16 Storage: ${caps.gpuInfo.supportFp16Storage}\n")
                    append("FP16 Arithmetic: ${caps.gpuInfo.supportFp16Arithmetic}\n")
                    append("FP16 Packed: ${caps.gpuInfo.supportFp16Packed}\n")
                } else {
                    append("GPU Error: ${caps.gpuInfo.errorMessage}\n")
                }
                append("\n")

                append("--- HARDWARE CODECS (MediaCodec) ---\n")
                append("AVC (H.264) Decoder: ${if (caps.avcDecoderAvailable) "Tersedia (HW)" else "Tidak tersedia"}\n")
                append("AVC (H.264) Encoder: ${if (caps.avcEncoderAvailable) "Tersedia (HW)" else "Tidak tersedia"}\n")
                append("HEVC (H.265) Decoder: ${if (caps.hevcDecoderAvailable) "Tersedia (HW)" else "Tidak tersedia"}\n")
                append("HEVC (H.265) Encoder: ${if (caps.hevcEncoderAvailable) "Tersedia (HW)" else "Tidak tersedia"}\n\n")

                append("Detail Decoders:\n")
                caps.hardwareDecoders.forEach { d ->
                    append("  - ${d.name} (${d.mimeType}): max ${d.maxSupportedWidth}x${d.maxSupportedHeight} @ ${d.maxSupportedFps.toInt()}fps\n")
                }
                append("\nDetail Encoders:\n")
                caps.hardwareEncoders.forEach { e ->
                    append("  - ${e.name} (${e.mimeType}): max ${e.maxSupportedWidth}x${e.maxSupportedHeight} @ ${e.maxSupportedFps.toInt()}fps\n")
                }
            }

            addBytesToZip(zos, "system_info.txt", sysInfoContent.toByteArray(Charsets.UTF_8))

            // 2. Add all log files
            val logFiles = HdLogger.getAllLogFiles()
            for (file in logFiles) {
                addFileToZip(zos, "logs/${file.name}", file)
            }

            // 3. Add latest breadcrumbs
            val breadcrumbFile = File(context.filesDir, "logs/breadcrumbs_latest.txt")
            if (breadcrumbFile.exists()) {
                addFileToZip(zos, "logs/breadcrumbs_latest.txt", breadcrumbFile)
            }

            // 4. Add all crash files
            val crashFiles = CrashHandler.getCrashFiles()
            for (file in crashFiles) {
                addFileToZip(zos, "crashes/${file.name}", file)
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private fun addBytesToZip(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun addFileToZip(zos: ZipOutputStream, entryName: String, file: File) {
        if (!file.exists()) return
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }
}
