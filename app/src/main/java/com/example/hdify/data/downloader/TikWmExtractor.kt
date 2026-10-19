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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TikWmExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun extract(url: String, platform: MediaPlatform): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val apiUrl = "https://www.tikwm.com/api/?url=$encoded&hd=1"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: Gagal menghubungi server TikWM"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respon kosong dari server"))
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val msg = json.optString("msg", "Gagal memproses URL")
                return@withContext Result.failure(Exception("TikWM Error: $msg"))
            }

            val data = json.getJSONObject("data")
            val title = data.optString("title", "Video ${platform.displayName}").ifBlank { "Media ${platform.displayName}" }
            val authorObj = data.optJSONObject("author")
            val author = authorObj?.optString("nickname") ?: authorObj?.optString("unique_id") ?: "Creator"
            val coverUrl = data.optString("cover").ifBlank { null }

            val options = mutableListOf<MediaDownloadOption>()
            val imagesArray = data.optJSONArray("images")
            val hasImages = imagesArray != null && imagesArray.length() > 0

            val hdplay = data.optString("hdplay")
            val play = data.optString("play")
            val hdSize = data.optLong("hd_size", 0L).takeIf { it > 0 }
            val sdSize = data.optLong("size", 0L).takeIf { it > 0 }

            if (hasImages) {
                // Multi-slide Carousel / Live Photos
                for (i in 0 until imagesArray.length()) {
                    val imgUrl = imagesArray.optString(i)
                    if (imgUrl.isNotBlank()) {
                        options.add(
                            MediaDownloadOption(
                                id = "photo_${i + 1}",
                                label = "Foto Slide ${i + 1} (Kualitas Asli)",
                                format = DownloadFormat.IMAGE,
                                url = imgUrl,
                                quality = "Original",
                                thumbnailUrl = imgUrl
                            )
                        )
                    }
                }

                // If motion video or slideshow video is also available (Live Photo / Animasi Slide)
                val motionUrl = hdplay.ifBlank { play }
                if (motionUrl.isNotBlank()) {
                    val fullMotionUrl = if (motionUrl.startsWith("http")) motionUrl else "https://www.tikwm.com$motionUrl"
                    options.add(
                        MediaDownloadOption(
                            id = "video_live_photo",
                            label = "Video Live Photo / Slide MP4 (HD)",
                            format = DownloadFormat.VIDEO,
                            url = fullMotionUrl,
                            quality = "HD 1080p",
                            sizeBytes = hdSize ?: sdSize,
                            thumbnailUrl = coverUrl ?: options.firstOrNull()?.thumbnailUrl
                        )
                    )
                }
            } else {
                // Standard Video post
                if (hdplay.isNotBlank()) {
                    val fullHdUrl = if (hdplay.startsWith("http")) hdplay else "https://www.tikwm.com$hdplay"
                    options.add(
                        MediaDownloadOption(
                            id = "video_hd",
                            label = "Video HD (Tanpa Watermark)",
                            format = DownloadFormat.VIDEO,
                            url = fullHdUrl,
                            quality = "HD 1080p",
                            sizeBytes = hdSize,
                            thumbnailUrl = coverUrl
                        )
                    )
                }

                if (play.isNotBlank()) {
                    val fullPlayUrl = if (play.startsWith("http")) play else "https://www.tikwm.com$play"
                    options.add(
                        MediaDownloadOption(
                            id = "video_sd",
                            label = if (options.isEmpty()) "Video (Tanpa Watermark)" else "Video Standard (SD)",
                            format = DownloadFormat.VIDEO,
                            url = fullPlayUrl,
                            quality = "SD 720p",
                            sizeBytes = sdSize,
                            thumbnailUrl = coverUrl
                        )
                    )
                }
            }

            // Audio MP3
            val music = data.optString("music")
            if (music.isNotBlank()) {
                val fullMusicUrl = if (music.startsWith("http")) music else "https://www.tikwm.com$music"
                options.add(
                    MediaDownloadOption(
                        id = "audio_mp3",
                        label = "Audio / Musik MP3",
                        format = DownloadFormat.AUDIO,
                        url = fullMusicUrl,
                        quality = "MP3",
                        thumbnailUrl = coverUrl
                    )
                )
            }

            if (options.isEmpty()) {
                return@withContext Result.failure(Exception("Tidak ditemukan media yang dapat diunduh dari link ini."))
            }

            HdLogger.i("TikWmExtractor", "Parsed ${options.size} download options for $url")
            Result.success(
                ExtractedMediaInfo(
                    platform = platform,
                    originalUrl = url,
                    title = title,
                    author = author,
                    thumbnailUrl = coverUrl,
                    options = options
                )
            )
        } catch (t: Throwable) {
            HdLogger.e("TikWmExtractor", null, "HD-DL-001", "Extraction failed for $url", t)
            Result.failure(t)
        }
    }
}
