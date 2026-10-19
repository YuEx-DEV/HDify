package com.example.hdify.data.downloader

import com.example.hdify.domain.downloader.DownloadFormat
import com.example.hdify.domain.downloader.ExtractedMediaInfo
import com.example.hdify.domain.downloader.MediaDownloadOption
import com.example.hdify.domain.downloader.MediaPlatform
import com.example.hdify.logging.HdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstagramExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val shortcodePattern = Pattern.compile("instagram\\.com/(?:p|reel|tv|stories)/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE)

    suspend fun extract(url: String): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        val matcher = shortcodePattern.matcher(url)
        val shortcode = if (matcher.find()) matcher.group(1) else "media"

        // Strategy 1: InDown Dynamic API
        val indownResult = extractFromInDown(url, shortcode ?: "media")
        if (indownResult.isSuccess) {
            return@withContext indownResult
        }

        // Strategy 2: Direct Embed Fallback
        val embedResult = extractFromEmbed(shortcode ?: "media", url)
        if (embedResult.isSuccess) {
            return@withContext embedResult
        }

        Result.failure(indownResult.exceptionOrNull() ?: Exception("Gagal mengekstrak media dari postingan Instagram."))
    }

    private fun extractFromInDown(targetUrl: String, shortcode: String): Result<ExtractedMediaInfo> {
        try {
            // Step 1: Fetch homepage for current tokens
            val homeRequest = Request.Builder()
                .url("https://indown.net/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val homeResp = client.newCall(homeRequest).execute()
            if (!homeResp.isSuccessful) return Result.failure(Exception("Gagal menghubungi server InDown"))

            val homeHtml = homeResp.body?.string() ?: ""
            val expMatcher = Pattern.compile("k_exp=\"([^\"]+)\"").matcher(homeHtml)
            val tokenMatcher = Pattern.compile("k_token=\"([^\"]+)\"").matcher(homeHtml)

            if (!expMatcher.find() || !tokenMatcher.find()) {
                return Result.failure(Exception("Gagal memvalidasi token generator"))
            }

            val kExp = expMatcher.group(1) ?: return Result.failure(Exception("Token k_exp kosong"))
            val kToken = tokenMatcher.group(1) ?: return Result.failure(Exception("Token k_token kosong"))

            // Step 2: Query ajaxSearch
            val formBody = FormBody.Builder()
                .add("k_exp", kExp)
                .add("k_token", kToken)
                .add("q", targetUrl)
                .add("t", "media")
                .add("lang", "en")
                .add("v", "v2")
                .build()

            val searchRequest = Request.Builder()
                .url("https://indown.net/api/ajaxSearch")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://indown.net/")
                .header("Origin", "https://indown.net")
                .build()

            val searchResp = client.newCall(searchRequest).execute()
            if (!searchResp.isSuccessful) return Result.failure(Exception("Gagal mengambil data video dari server"))

            val searchBody = searchResp.body?.string() ?: ""
            val json = JSONObject(searchBody)
            if (json.optString("status") != "ok") {
                return Result.failure(Exception(json.optString("mess", "Gagal memproses media Instagram")))
            }

            val htmlData = json.optString("data", "")
            val options = mutableListOf<MediaDownloadOption>()

            val liPattern = Pattern.compile("(?s)<li>(.*?)</li>")
            val liMatcher = liPattern.matcher(htmlData)
            val liBlocks = mutableListOf<String>()
            while (liMatcher.find()) {
                liMatcher.group(1)?.let { liBlocks.add(it) }
            }

            val totalItems = liBlocks.size
            var mainThumbnailUrl: String? = null

            if (totalItems > 0) {
                for ((index, itemHtml) in liBlocks.withIndex()) {
                    val slideNum = index + 1
                    val isVideo = itemHtml.contains("icon-dlvideo", ignoreCase = true) ||
                            itemHtml.contains("Download Video", ignoreCase = true)

                    // Extract thumbnail for this slide
                    var itemThumb: String? = null
                    val thumbMatcher = Pattern.compile("src=[\"'](https?://[^\"']+)[\"']").matcher(itemHtml)
                    if (thumbMatcher.find()) {
                        itemThumb = thumbMatcher.group(1)
                    } else {
                        val dataSrcMatcher = Pattern.compile("data-src=[\"'](https?://[^\"']+)[\"']").matcher(itemHtml)
                        if (dataSrcMatcher.find()) {
                            itemThumb = dataSrcMatcher.group(1)
                        }
                    }

                    if (mainThumbnailUrl == null && itemThumb != null) {
                        mainThumbnailUrl = itemThumb
                    }

                    if (isVideo) {
                        var videoUrl: String? = null
                        val vMatcher1 = Pattern.compile("(?i)<a[^>]*title=[\"']Download Video[\"'][^>]*href=[\"'](https?://[^\"']+)[\"']").matcher(itemHtml)
                        if (vMatcher1.find()) {
                            videoUrl = vMatcher1.group(1)
                        } else {
                            val vMatcher2 = Pattern.compile("(?i)<a[^>]*href=[\"'](https?://[^\"']+)[\"'][^>]*title=[\"']Download Video[\"']").matcher(itemHtml)
                            if (vMatcher2.find()) {
                                videoUrl = vMatcher2.group(1)
                            }
                        }

                        if (!videoUrl.isNullOrBlank()) {
                            val label = if (totalItems > 1) "Video Slide $slideNum (Kualitas Terbaik)" else "Video MP4 (Kualitas Terbaik)"
                            options.add(
                                MediaDownloadOption(
                                    id = "ig_video_$slideNum",
                                    label = label,
                                    format = DownloadFormat.VIDEO,
                                    url = videoUrl,
                                    quality = "HD",
                                    thumbnailUrl = itemThumb
                                )
                            )
                        }
                    } else {
                        // Image slide
                        var imgUrl: String? = null
                        var quality = "Original"

                        // Check select option for highest resolution
                        val optMatcher = Pattern.compile("(?i)<option[^>]*value=[\"'](https?://[^\"']+)[\"'][^>]*>([^<]*)").matcher(itemHtml)
                        if (optMatcher.find()) {
                            imgUrl = optMatcher.group(1)
                            quality = optMatcher.group(2)?.trim()?.takeIf { it.isNotBlank() } ?: "Original"
                        } else {
                            val imgMatcher1 = Pattern.compile("(?i)<a[^>]*title=[\"']Download Image[\"'][^>]*href=[\"'](https?://[^\"']+)[\"']").matcher(itemHtml)
                            if (imgMatcher1.find()) {
                                imgUrl = imgMatcher1.group(1)
                            } else {
                                val imgMatcher2 = Pattern.compile("(?i)<a[^>]*href=[\"'](https?://[^\"']+)[\"'][^>]*title=[\"']Download Image[\"']").matcher(itemHtml)
                                if (imgMatcher2.find()) {
                                    imgUrl = imgMatcher2.group(1)
                                }
                            }
                        }

                        if (!imgUrl.isNullOrBlank()) {
                            val label = if (totalItems > 1) "Foto Slide $slideNum (Kualitas Asli)" else "Foto (Kualitas Asli)"
                            options.add(
                                MediaDownloadOption(
                                    id = "ig_photo_$slideNum",
                                    label = label,
                                    format = DownloadFormat.IMAGE,
                                    url = imgUrl,
                                    quality = quality,
                                    thumbnailUrl = itemThumb ?: imgUrl
                                )
                            )
                        }
                    }
                }
            } else {
                // Fallback for non-li single item responses
                val isVideo = htmlData.contains("Download Video", ignoreCase = true) || targetUrl.contains("/reel/", ignoreCase = true)
                val thumbMatcher = Pattern.compile("src=[\"'](https?://[^\"']+)[\"']").matcher(htmlData)
                if (thumbMatcher.find()) {
                    mainThumbnailUrl = thumbMatcher.group(1)
                }

                if (isVideo) {
                    val vMatcher = Pattern.compile("(?i)<a[^>]*title=[\"']Download Video[\"'][^>]*href=[\"'](https?://[^\"']+)[\"']").matcher(htmlData)
                    val url = if (vMatcher.find()) vMatcher.group(1) else null
                    if (!url.isNullOrBlank()) {
                        options.add(
                            MediaDownloadOption(
                                id = "ig_video_1",
                                label = "Video MP4 (Kualitas Terbaik)",
                                format = DownloadFormat.VIDEO,
                                url = url,
                                quality = "HD",
                                thumbnailUrl = mainThumbnailUrl
                            )
                        )
                    }
                }

                if (options.isEmpty()) {
                    val imgMatcher = Pattern.compile("(?i)<a[^>]*title=[\"']Download Image[\"'][^>]*href=[\"'](https?://[^\"']+)[\"']").matcher(htmlData)
                    val url = if (imgMatcher.find()) imgMatcher.group(1) else null
                    if (!url.isNullOrBlank()) {
                        options.add(
                            MediaDownloadOption(
                                id = "ig_photo_1",
                                label = "Foto (Kualitas Asli)",
                                format = DownloadFormat.IMAGE,
                                url = url,
                                quality = "Original",
                                thumbnailUrl = mainThumbnailUrl ?: url
                            )
                        )
                    }
                }
            }

            if (options.isEmpty()) {
                return Result.failure(Exception("Link unduhan Instagram tidak ditemukan atau akun di-private."))
            }

            val isReel = targetUrl.contains("/reel/", ignoreCase = true)
            val title = if (isReel) "Instagram Reel ($shortcode)" else "Instagram Post ($shortcode)"

            return Result.success(
                ExtractedMediaInfo(
                    platform = MediaPlatform.INSTAGRAM,
                    originalUrl = targetUrl,
                    title = title,
                    author = "Instagram Creator",
                    thumbnailUrl = mainThumbnailUrl ?: options.firstOrNull()?.thumbnailUrl,
                    options = options
                )
            )
        } catch (t: Throwable) {
            HdLogger.e("InstagramExtractor", null, "HD-DL-003", "InDown extraction failed", t)
            return Result.failure(t)
        }
    }

    private fun extractFromEmbed(shortcode: String, originalUrl: String): Result<ExtractedMediaInfo> {
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("Embed HTTP ${response.code}"))

            val html = response.body?.string() ?: ""
            // Look for display_url in embed
            val displayPattern = Pattern.compile("display_url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']")
            val m = displayPattern.matcher(html)
            if (m.find()) {
                val rawGroup = m.group(1) ?: return Result.failure(Exception("URL embed kosong"))
                val imgUrl = rawGroup.replace("\\u0026", "&").replace("\\/", "/")
                return Result.success(
                    ExtractedMediaInfo(
                        platform = MediaPlatform.INSTAGRAM,
                        originalUrl = originalUrl,
                        title = "Instagram Post ($shortcode)",
                        author = "Instagram User",
                        thumbnailUrl = imgUrl,
                        options = listOf(
                            MediaDownloadOption(
                                id = "ig_embed_image",
                                label = "Foto (Kualitas Tinggi)",
                                format = DownloadFormat.IMAGE,
                                url = imgUrl,
                                quality = "High"
                            )
                        )
                    )
                )
            }
            return Result.failure(Exception("Media embed tidak ditemukan"))
        } catch (t: Throwable) {
            return Result.failure(t)
        }
    }
}
