package com.example.hdify.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.hdify.BuildConfig
import com.example.hdify.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GitHubUpdateChecker {

    private const val GITHUB_REPO_API = "https://api.github.com/repos/YuEx-DEV/HDify/releases/latest"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    data class UpdateResult(
        val hasUpdate: Boolean,
        val latestTag: String,
        val releaseName: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val apkDownloadUrl: String?
    )

    fun checkForUpdate(context: Context, isManual: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = performCheck()
                withContext(Dispatchers.Main) {
                    if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                        return@withContext
                    }

                    if (result.hasUpdate) {
                        showUpdateDialog(context, result)
                    } else if (isManual) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_already_latest, BuildConfig.VERSION_NAME),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isManual) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_check_failed, e.localizedMessage ?: "Unknown error"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun performCheck(): UpdateResult {
        val request = Request.Builder()
            .url(GITHUB_REPO_API)
            .header("User-Agent", "HDify-Android-v${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}")
        }

        val bodyStr = response.body?.string() ?: throw IllegalStateException("Empty response")
        val json = JSONObject(bodyStr)
        val tag = json.optString("tag_name", "").trim()
        val latestVersion = tag.removePrefix("v").trim()
        val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

        val hasUpdate = isVersionNewer(latestVersion, currentVersion)
        val releaseName = json.optString("name", tag)
        val releaseNotes = json.optString("body", "")
        val releaseUrl = json.optString("html_url", "https://github.com/YuEx-DEV/HDify/releases")

        var apkDownloadUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }

        return UpdateResult(
            hasUpdate = hasUpdate,
            latestTag = tag,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            releaseUrl = releaseUrl,
            apkDownloadUrl = apkDownloadUrl
        )
    }

    private fun isVersionNewer(newVer: String, curVer: String): Boolean {
        val nParts = newVer.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = curVer.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(nParts.size, cParts.size)

        for (i in 0 until maxLen) {
            val n = nParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }

    private fun showUpdateDialog(context: Context, result: UpdateResult) {
        val message = buildString {
            append(context.getString(R.string.update_available_msg, result.latestTag))
            if (result.releaseNotes.isNotBlank()) {
                append("\n\n")
                val cleanNotes = result.releaseNotes.lines().take(8).joinToString("\n")
                append(cleanNotes)
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_download_apk) { _, _ ->
                val targetUrl = result.apkDownloadUrl ?: result.releaseUrl
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                context.startActivity(intent)
            }
            .setNeutralButton(R.string.btn_view_release) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.releaseUrl))
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.btn_later, null)
            .show()
    }
}
