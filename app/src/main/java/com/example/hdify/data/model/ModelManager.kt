package com.example.hdify.data.model

import android.content.Context
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ModelSpec(
    val directory: String,      // "models-se", "models-pro", or "realesrgan"
    val scale: Int,             // 2, 3, 4
    val denoise: Int,           // -1..3
    val name: String,
    val paramFile: String,
    val binFile: String
)

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val checksumMap = mutableMapOf<String, String>()

    init {
        loadChecksums()
    }

    private fun loadChecksums() {
        // Load Real-CUGAN checksums
        try {
            val jsonStr = context.assets.open("models/realcugan/checksums.json").bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            jsonObj.keys().forEach { key ->
                checksumMap["realcugan/$key"] = jsonObj.getString(key)
            }
        } catch (e: Exception) {
            HdLogger.w(TAG, "Failed to load realcugan checksums: ${e.message}")
        }

        // Load Real-ESRGAN checksums
        try {
            val jsonStr = context.assets.open("models/realesrgan/checksums.json").bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            jsonObj.keys().forEach { key ->
                checksumMap["realesrgan/$key"] = jsonObj.getString(key)
            }
        } catch (e: Exception) {
            HdLogger.w(TAG, "Failed to load realesrgan checksums: ${e.message}")
        }

        HdLogger.d(TAG, "Loaded total ${checksumMap.size} model checksums")
    }

    fun isModelSupported(directory: String, scale: Int, denoise: Int): Boolean {
        if (directory == "models-pro") {
            if (scale == 4) return false // Pro has no 4x native
            return denoise in listOf(-1, 0, 3)
        }
        if (directory == "models-se") {
            if (scale == 2) return denoise in listOf(-1, 0, 1, 2, 3)
            if (scale in listOf(3, 4)) return denoise in listOf(-1, 0, 3)
        }
        return false
    }

    fun isRealEsrganSupported(modelName: String, scale: Int): Boolean {
        return when (modelName) {
            "realesrgan-x4plus", "realesrgan-x4plus-anime" -> scale in listOf(2, 3, 4) // x4 native with downscale support
            "realesr-animevideov3-x2" -> scale == 2
            "realesr-animevideov3-x3" -> scale == 3
            "realesr-animevideov3-x4" -> scale == 4
            else -> false
        }
    }

    fun getModelFileName(scale: Int, denoise: Int, isBin: Boolean): String {
        val ext = if (isBin) "bin" else "param"
        return when (denoise) {
            -1 -> "up${scale}x-conservative.$ext"
            0 -> "up${scale}x-no-denoise.$ext"
            else -> "up${scale}x-denoise${denoise}x.$ext"
        }
    }

    fun verifyModelChecksum(engineDir: String, relativePath: String): Boolean {
        val key = "$engineDir/$relativePath"
        val expected = checksumMap[key] ?: return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("models/$engineDir/$relativePath").use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val matches = actual.equals(expected, ignoreCase = true)
            if (!matches) {
                HdLogger.e(TAG, "HD-MDL-203 Checksum mismatch for $key: expected $expected, got $actual")
            }
            matches
        } catch (e: Exception) {
            HdLogger.e(TAG, "HD-MDL-203 Failed to verify checksum for $key: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
