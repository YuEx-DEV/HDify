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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitterExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val tweetIdPattern = Pattern.compile("(?:twitter|x|vxtwitter|fxtwitter|fixupx)\\.com/(?:#!/)?(?:i/status/|i/web/status/|[\\w.-]+/status/)(\\d+)", Pattern.CASE_INSENSITIVE)

    suspend fun extract(url: String): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        val matcher = tweetIdPattern.matcher(url)
        if (!matcher.find()) {
            return@withContext Result.failure(Exception("Format link Twitter/X tidak valid. Pastikan link memiliki ID status (contoh: x.com/user/status/123...)."))
        }

        val tweetId = matcher.group(1) ?: return@withContext Result.failure(Exception("ID Tweet tidak ditemukan."))

        // Strategy 1: FxTwitter Public API (very reliable for modern X)
        val fxResult = extractFromFxTwitter(tweetId, url)
        if (fxResult.isSuccess) {
            return@withContext fxResult
        }

        // Strategy 2: Twitter Syndication Embed API
        val syndicationResult = extractFromSyndication(tweetId, url)
        if (syndicationResult.isSuccess) {
            return@withContext syndicationResult
        }

        // Strategy 3: VxTwitter API fallback
        val vxResult = extractFromVxTwitter(tweetId, url)
        if (vxResult.isSuccess) {
            return@withContext vxResult
        }

        Result.failure(fxResult.exceptionOrNull() ?: Exception("Gagal mengekstrak media dari postingan Twitter/X."))
    }

    private fun extractFromSyndication(tweetId: String, originalUrl: String): Result<ExtractedMediaInfo> {
        try {
            val apiUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en&token=a"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://platform.twitter.com")
                .header("Referer", "https://platform.twitter.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Syndication API HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = JSONObject(body)

            val text = json.optString("text", "Postingan X/Twitter").replace(Regex("https?://t\\.co/\\w+"), "").trim()
            val userObj = json.optJSONObject("user")
            val author = userObj?.optString("name") ?: userObj?.optString("screen_name") ?: "Twitter User"

            val options = mutableListOf<MediaDownloadOption>()
            var thumbnailUrl: String? = null

            // Check video
            val videoObj = json.optJSONObject("video")
            if (videoObj != null) {
                thumbnailUrl = videoObj.optString("poster").takeIf { it.isNotBlank() }
                val variantsArray = videoObj.optJSONArray("variants")
                if (variantsArray != null) {
                    val mp4Variants = mutableListOf<JSONObject>()
                    for (i in 0 until variantsArray.length()) {
                        val v = variantsArray.optJSONObject(i) ?: continue
                        val ct = v.optString("type").ifBlank { v.optString("content_type") }
                        if (ct.contains("mp4", ignoreCase = true)) {
                            mp4Variants.add(v)
                        }
                    }

                    // Sort by bitrate descending
                    mp4Variants.sortByDescending { it.optLong("bitrate", 0L) }

                    for ((idx, variant) in mp4Variants.withIndex()) {
                        val videoUrl = variant.optString("src").ifBlank { variant.optString("url") }
                        if (videoUrl.isBlank()) continue

                        val bitrate = variant.optLong("bitrate", 0L)
                        val resolution = extractResolutionFromUrl(videoUrl) ?: if (bitrate > 1_500_000) "1080p" else if (bitrate > 800_000) "720p" else "480p"

                        val label = when (idx) {
                            0 -> "Video MP4 HD ($resolution)"
                            1 -> "Video MP4 Medium ($resolution)"
                            else -> "Video MP4 SD ($resolution)"
                        }

                        options.add(
                            MediaDownloadOption(
                                id = "twitter_video_$idx",
                                label = label,
                                format = DownloadFormat.VIDEO,
                                url = videoUrl,
                                quality = resolution,
                                thumbnailUrl = thumbnailUrl
                            )
                        )
                    }
                }
            }

            // Check photos
            val photosArray = json.optJSONArray("photos")
            if (photosArray != null && photosArray.length() > 0) {
                for (i in 0 until photosArray.length()) {
                    val p = photosArray.optJSONObject(i) ?: continue
                    val photoUrl = p.optString("url")
                    if (photoUrl.isNotBlank()) {
                        if (thumbnailUrl == null) thumbnailUrl = photoUrl
                        val highResUrl = if (photoUrl.contains("?")) photoUrl else "$photoUrl?name=orig"
                        options.add(
                            MediaDownloadOption(
                                id = "twitter_photo_${i + 1}",
                                label = "Foto Slide ${i + 1} (Kualitas Asli)",
                                format = DownloadFormat.IMAGE,
                                url = highResUrl,
                                quality = "Original",
                                thumbnailUrl = photoUrl
                            )
                        )
                    }
                }
            }

            if (options.isEmpty()) {
                return Result.failure(Exception("Postingan ini hanya berisi teks atau media tidak dapat diakses."))
            }

            return Result.success(
                ExtractedMediaInfo(
                    platform = MediaPlatform.TWITTER_X,
                    originalUrl = originalUrl,
                    title = text.ifBlank { "Postingan Twitter/X" },
                    author = author,
                    thumbnailUrl = thumbnailUrl,
                    options = options
                )
            )
        } catch (t: Throwable) {
            HdLogger.e("TwitterExtractor", null, "HD-DL-002", "Syndication parse failed", t)
            return Result.failure(t)
        }
    }

    private fun extractFromFxTwitter(tweetId: String, originalUrl: String): Result<ExtractedMediaInfo> {
        try {
            val apiUrl = "https://api.fxtwitter.com/i/status/$tweetId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "TelegramBot (like TwitterBot)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("FxTwitter HTTP ${response.code}"))

            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = JSONObject(body)
            val tweet = json.optJSONObject("tweet") ?: return Result.failure(Exception("No tweet in response"))

            val text = tweet.optString("text", "Postingan X/Twitter")
            val authorObj = tweet.optJSONObject("author")
            val author = authorObj?.optString("name") ?: "Twitter User"

            val mediaObj = tweet.optJSONObject("media") ?: return Result.failure(Exception("No media in tweet"))
            val options = mutableListOf<MediaDownloadOption>()
            var thumbnailUrl: String? = null

            // Videos
            val videos = mediaObj.optJSONArray("videos")
            if (videos != null && videos.length() > 0) {
                for (i in 0 until videos.length()) {
                    val v = videos.optJSONObject(i) ?: continue
                    val vUrl = v.optString("url")
                    if (vUrl.isNotBlank()) {
                        val vThumb = v.optString("thumbnail_url").takeIf { it.isNotBlank() }
                        if (thumbnailUrl == null) thumbnailUrl = vThumb
                        options.add(
                            MediaDownloadOption(
                                id = "fx_video_$i",
                                label = "Video MP4 (HD)",
                                format = DownloadFormat.VIDEO,
                                url = vUrl,
                                quality = "HD",
                                thumbnailUrl = vThumb ?: thumbnailUrl
                            )
                        )
                    }
                }
            }

            // Photos
            val photos = mediaObj.optJSONArray("photos")
            if (photos != null && photos.length() > 0) {
                for (i in 0 until photos.length()) {
                    val p = photos.optJSONObject(i) ?: continue
                    val pUrl = p.optString("url")
                    if (pUrl.isNotBlank()) {
                        if (thumbnailUrl == null) thumbnailUrl = pUrl
                        options.add(
                            MediaDownloadOption(
                                id = "fx_photo_$i",
                                label = "Foto Slide ${i + 1} (Kualitas Asli)",
                                format = DownloadFormat.IMAGE,
                                url = pUrl,
                                quality = "Original",
                                thumbnailUrl = pUrl
                            )
                        )
                    }
                }
            }

            if (options.isEmpty()) return Result.failure(Exception("No downloadable media"))

            return Result.success(
                ExtractedMediaInfo(
                    platform = MediaPlatform.TWITTER_X,
                    originalUrl = originalUrl,
                    title = text,
                    author = author,
                    thumbnailUrl = thumbnailUrl,
                    options = options
                )
            )
        } catch (t: Throwable) {
            return Result.failure(t)
        }
    }

    private fun extractFromVxTwitter(tweetId: String, originalUrl: String): Result<ExtractedMediaInfo> {
        try {
            val apiUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("VxTwitter HTTP ${response.code}"))

            val body = response.body?.string() ?: return Result.failure(Exception("Empty body from VxTwitter"))
            val json = JSONObject(body)

            val text = json.optString("text", "Postingan X/Twitter")
            val author = json.optString("user_name", json.optString("user_screen_name", "Twitter User"))

            val options = mutableListOf<MediaDownloadOption>()
            var thumbnailUrl: String? = null

            val mediaExtended = json.optJSONArray("media_extended")
            if (mediaExtended != null && mediaExtended.length() > 0) {
                var vCount = 0
                var pCount = 0
                for (i in 0 until mediaExtended.length()) {
                    val m = mediaExtended.optJSONObject(i) ?: continue
                    val mUrl = m.optString("url")
                    val mType = m.optString("type")
                    val mThumb = m.optString("thumbnail_url").ifBlank { null }
                    if (thumbnailUrl == null) thumbnailUrl = mThumb ?: mUrl

                    if (mType.contains("video") || mUrl.contains(".mp4")) {
                        vCount++
                        options.add(
                            MediaDownloadOption(
                                id = "vx_video_$vCount",
                                label = "Video MP4 (HD)",
                                format = DownloadFormat.VIDEO,
                                url = mUrl,
                                quality = "HD",
                                thumbnailUrl = mThumb ?: thumbnailUrl
                            )
                        )
                    } else {
                        pCount++
                        val fullUrl = if (mUrl.contains("?")) mUrl else "$mUrl?name=orig"
                        options.add(
                            MediaDownloadOption(
                                id = "vx_photo_$pCount",
                                label = "Foto $pCount (Kualitas Asli)",
                                format = DownloadFormat.IMAGE,
                                url = fullUrl,
                                quality = "Original",
                                thumbnailUrl = mUrl
                            )
                        )
                    }
                }
            } else {
                val mediaUrls = json.optJSONArray("mediaURLs")
                if (mediaUrls != null && mediaUrls.length() > 0) {
                    for (i in 0 until mediaUrls.length()) {
                        val mUrl = mediaUrls.optString(i)
                        if (mUrl.isBlank()) continue
                        if (thumbnailUrl == null) thumbnailUrl = mUrl
                        val isVid = mUrl.contains(".mp4")
                        options.add(
                            MediaDownloadOption(
                                id = "vx_m_$i",
                                label = if (isVid) "Video MP4" else "Foto ${i + 1}",
                                format = if (isVid) DownloadFormat.VIDEO else DownloadFormat.IMAGE,
                                url = mUrl,
                                quality = if (isVid) "HD" else "Original",
                                thumbnailUrl = mUrl
                            )
                        )
                    }
                }
            }

            if (options.isEmpty()) return Result.failure(Exception("Media Twitter tidak ditemukan"))

            return Result.success(
                ExtractedMediaInfo(
                    platform = MediaPlatform.TWITTER_X,
                    originalUrl = originalUrl,
                    title = text,
                    author = author,
                    thumbnailUrl = thumbnailUrl,
                    options = options
                )
            )
        } catch (t: Throwable) {
            return Result.failure(t)
        }
    }

    private fun extractResolutionFromUrl(url: String): String? {
        val m = Pattern.compile("/(\\d+x\\d+)/").matcher(url)
        return if (m.find()) m.group(1) else null
    }
}
