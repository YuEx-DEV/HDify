package com.example.hdify.data.downloader

import com.example.hdify.domain.downloader.DownloadFormat
import com.example.hdify.domain.downloader.ExtractedMediaInfo
import com.example.hdify.domain.downloader.MediaDownloadOption
import com.example.hdify.domain.downloader.MediaPlatform
import com.example.hdify.logging.HdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val ytVideoIdPattern = Pattern.compile(
        "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:embed/|v/|shorts/|live/|watch\\?v=|watch\\?.+&v=|music\\.youtube\\.com/watch\\?v=))([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    private val invidiousInstances = listOf(
        "https://invidious.f5.si",
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://iv.melmac.space"
    )

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de"
    )

    suspend fun extract(url: String, platform: MediaPlatform = MediaPlatform.YOUTUBE): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        val matcher = ytVideoIdPattern.matcher(url)
        if (!matcher.find()) {
            return@withContext Result.failure(Exception("Format link YouTube tidak valid. Pastikan link berisi ID video YouTube yang benar."))
        }

        val videoId = matcher.group(1) ?: return@withContext Result.failure(Exception("ID Video YouTube tidak ditemukan."))

        // Strategy 1: Invidious API
        for (instance in invidiousInstances) {
            val result = extractFromInvidious(instance, videoId, url, platform)
            if (result.isSuccess) {
                return@withContext result
            }
        }

        // Strategy 2: Piped API
        for (instance in pipedInstances) {
            val result = extractFromPiped(instance, videoId, url, platform)
            if (result.isSuccess) {
                return@withContext result
            }
        }

        Result.failure(Exception("Gagal mengekstrak media dari YouTube. Server publik YouTube sedang membatasi akses atau video bersifat privat."))
    }

    private fun extractFromInvidious(
        instance: String,
        videoId: String,
        originalUrl: String,
        platform: MediaPlatform
    ): Result<ExtractedMediaInfo> {
        try {
            val apiUrl = "$instance/api/v1/videos/$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Invidious $instance returned ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response body"))
            val json = JSONObject(body)

            val title = json.optString("title", "Video YouTube")
            val author = json.optString("author", "YouTube Creator")

            var thumbnailUrl: String? = null
            val thumbsArray = json.optJSONArray("videoThumbnails")
            if (thumbsArray != null && thumbsArray.length() > 0) {
                val lastThumb = thumbsArray.optJSONObject(thumbsArray.length() - 1)
                thumbnailUrl = lastThumb?.optString("url")
            }
            if (thumbnailUrl.isNullOrBlank()) {
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            val options = mutableListOf<MediaDownloadOption>()

            // 1. Progressive video streams (with audio included)
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val stream = formatStreams.optJSONObject(i) ?: continue
                    val streamUrl = stream.optString("url")
                    if (streamUrl.isBlank()) continue

                    val res = stream.optString("resolution", "360p")
                    val isHd = res.contains("720") || res.contains("1080")
                    val label = if (isHd) "Video HD ($res)" else "Video Standard ($res)"

                    options.add(
                        MediaDownloadOption(
                            id = "yt_prog_${res}_$i",
                            label = label,
                            format = DownloadFormat.VIDEO,
                            url = streamUrl,
                            quality = res
                        )
                    )
                }
            }

            // 2. Audio streams
            val adaptiveFormats = json.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val stream = adaptiveFormats.optJSONObject(i) ?: continue
                    val type = stream.optString("type")
                    if (!type.startsWith("audio/")) continue

                    val streamUrl = stream.optString("url")
                    if (streamUrl.isBlank()) continue

                    val quality = stream.optString("audioQuality", "Medium")
                        .replace("AUDIO_QUALITY_", "")
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }

                    val label = "Audio / Musik MP3 ($quality)"
                    options.add(
                        MediaDownloadOption(
                            id = "yt_audio_$i",
                            label = label,
                            format = DownloadFormat.AUDIO,
                            url = streamUrl,
                            quality = "MP3"
                        )
                    )
                    break // Keep the best audio stream
                }
            }

            if (options.isEmpty()) {
                return Result.failure(Exception("Tidak ada stream video/audio yang dapat diunduh."))
            }

            // For YouTube Music, put audio option at the top
            if (platform == MediaPlatform.YOUTUBE_MUSIC) {
                val audioOpt = options.firstOrNull { it.format == DownloadFormat.AUDIO }
                if (audioOpt != null) {
                    options.remove(audioOpt)
                    options.add(0, audioOpt)
                }
            }

            return Result.success(
                ExtractedMediaInfo(
                    platform = platform,
                    originalUrl = originalUrl,
                    title = title,
                    author = author,
                    thumbnailUrl = thumbnailUrl,
                    options = options
                )
            )
        } catch (e: Exception) {
            HdLogger.w("YouTubeExtractor", "Invidious extraction error: ${e.message}")
            return Result.failure(e)
        }
    }

    private fun extractFromPiped(
        instance: String,
        videoId: String,
        originalUrl: String,
        platform: MediaPlatform
    ): Result<ExtractedMediaInfo> {
        try {
            val apiUrl = "$instance/streams/$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Piped $instance returned ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = JSONObject(body)

            val title = json.optString("title", "Video YouTube")
            val author = json.optString("uploader", "YouTube Creator")
            val thumbnail = json.optString("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")

            val options = mutableListOf<MediaDownloadOption>()

            val videoStreams = json.optJSONArray("videoStreams")
            if (videoStreams != null) {
                for (i in 0 until videoStreams.length()) {
                    val stream = videoStreams.optJSONObject(i) ?: continue
                    val streamUrl = stream.optString("url")
                    if (streamUrl.isBlank()) continue
                    val quality = stream.optString("quality", "720p")
                    val isHd = quality.contains("720") || quality.contains("1080")
                    options.add(
                        MediaDownloadOption(
                            id = "piped_video_$i",
                            label = if (isHd) "Video HD ($quality)" else "Video ($quality)",
                            format = DownloadFormat.VIDEO,
                            url = streamUrl,
                            quality = quality,
                            thumbnailUrl = thumbnail
                        )
                    )
                    if (options.size >= 2) break
                }
            }

            val audioStreams = json.optJSONArray("audioStreams")
            if (audioStreams != null && audioStreams.length() > 0) {
                val aStream = audioStreams.optJSONObject(0)
                val aUrl = aStream?.optString("url")
                if (!aUrl.isNullOrBlank()) {
                    options.add(
                        MediaDownloadOption(
                            id = "piped_audio",
                            label = "Audio / Musik MP3",
                            format = DownloadFormat.AUDIO,
                            url = aUrl,
                            quality = "MP3",
                            thumbnailUrl = thumbnail
                        )
                    )
                }
            }

            if (options.isEmpty()) {
                return Result.failure(Exception("No streams found on Piped"))
            }

            if (platform == MediaPlatform.YOUTUBE_MUSIC) {
                val audioOpt = options.firstOrNull { it.format == DownloadFormat.AUDIO }
                if (audioOpt != null) {
                    options.remove(audioOpt)
                    options.add(0, audioOpt)
                }
            }

            return Result.success(
                ExtractedMediaInfo(
                    platform = platform,
                    originalUrl = originalUrl,
                    title = title,
                    author = author,
                    thumbnailUrl = thumbnail,
                    options = options
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
