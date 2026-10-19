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
class SoundCloudExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Verified active SoundCloud client IDs
    private val clientIds = listOf(
        "tHzjkKIyQ5PCLhppl7qomsm6HKpf16tW",
        "a3e059563d7fd3372b49b37f00a00bcf",
        "2t9loNfh90kzJvxKyconeq9wOqv52e0m"
    )

    suspend fun extract(url: String): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        try {
            // Follow redirect if shortened URL (e.g. on.soundcloud.com)
            val canonicalUrl = resolveCanonicalUrl(url)
            val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")

            // 1. Fetch metadata via oEmbed
            val oembedReq = Request.Builder()
                .url("https://soundcloud.com/oembed?format=json&url=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val oembedResp = client.newCall(oembedReq).execute()
            var title = "Lagu SoundCloud"
            var author = "SoundCloud Artist"
            var artwork: String? = null

            if (oembedResp.isSuccessful) {
                val oembedBody = oembedResp.body?.string().orEmpty()
                if (oembedBody.isNotBlank()) {
                    val oembedJson = JSONObject(oembedBody)
                    title = oembedJson.optString("title", title)
                        .replace(" by " + oembedJson.optString("author_name"), "")
                        .trim()
                    author = oembedJson.optString("author_name", author)
                    artwork = oembedJson.optString("thumbnail_url").takeIf { it.isNotBlank() }
                }
            }

            // 2. Resolve audio stream via api-v2
            for (clientId in clientIds) {
                val resolveResult = resolveAudioStream(canonicalUrl, clientId, title, author, artwork)
                if (resolveResult.isSuccess) {
                    return@withContext resolveResult
                }
            }

            Result.failure(Exception("Gagal mengambil stream audio SoundCloud. Pastikan lagu berstatus publik."))
        } catch (e: Exception) {
            HdLogger.e("SoundCloudExtractor", "SoundCloud extraction error", e)
            Result.failure(e)
        }
    }

    private fun resolveCanonicalUrl(url: String): String {
        if (!url.contains("on.soundcloud.com")) return url
        try {
            val req = Request.Builder()
                .url(url)
                .head()
                .build()
            val resp = client.newCall(req).execute()
            val finalUrl = resp.request.url.toString()
            if (finalUrl.isNotBlank()) return finalUrl
        } catch (_: Throwable) {}
        return url
    }

    private fun resolveAudioStream(
        canonicalUrl: String,
        clientId: String,
        title: String,
        author: String,
        artwork: String?
    ): Result<ExtractedMediaInfo> {
        try {
            val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
            val resolveApi = "https://api-v2.soundcloud.com/resolve?url=$encoded&client_id=$clientId"

            val req = Request.Builder()
                .url(resolveApi)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("HTTP ${resp.code}"))
            }

            val body = resp.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = JSONObject(body)

            val trackTitle = json.optString("title").ifBlank { title }
            val userObj = json.optJSONObject("user")
            val trackAuthor = userObj?.optString("username")?.ifBlank { author } ?: author
            val rawArtwork = json.optString("artwork_url").ifBlank { artwork.orEmpty() }.ifBlank { userObj?.optString("avatar_url").orEmpty() }
            val highResArtwork = if (rawArtwork.isNotBlank()) {
                rawArtwork.replace("-large.", "-t500x500.")
            } else null

            val mediaObj = json.optJSONObject("media")
            val transcodings = mediaObj?.optJSONArray("transcodings")
            if (transcodings == null || transcodings.length() == 0) {
                return Result.failure(Exception("Tidak ada media transcodings"))
            }

            var chosenTranscodingUrl: String? = null

            // Prioritize progressive stream (direct MP3)
            for (i in 0 until transcodings.length()) {
                val tc = transcodings.optJSONObject(i) ?: continue
                val formatObj = tc.optJSONObject("format")
                val protocol = formatObj?.optString("protocol")
                if (protocol == "progressive") {
                    chosenTranscodingUrl = tc.optString("url")
                    break
                }
            }

            // Fallback to first transcoding if no progressive
            if (chosenTranscodingUrl == null) {
                chosenTranscodingUrl = transcodings.optJSONObject(0)?.optString("url")
            }

            if (chosenTranscodingUrl.isNullOrBlank()) {
                return Result.failure(Exception("Transcoding URL kosong"))
            }

            // Fetch final stream URL
            val streamReq = Request.Builder()
                .url("$chosenTranscodingUrl?client_id=$clientId")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()

            val streamResp = client.newCall(streamReq).execute()
            if (!streamResp.isSuccessful) {
                return Result.failure(Exception("Stream HTTP ${streamResp.code}"))
            }

            val streamBody = streamResp.body?.string().orEmpty()
            val streamJson = JSONObject(streamBody)
            val finalDownloadUrl = streamJson.optString("url")

            if (finalDownloadUrl.isBlank()) {
                return Result.failure(Exception("Direct stream URL kosong"))
            }

            val options = mutableListOf<MediaDownloadOption>()
            options.add(
                MediaDownloadOption(
                    id = "sc_audio_mp3",
                    label = "Audio / Musik MP3 (Kualitas Asli)",
                    format = DownloadFormat.AUDIO,
                    url = finalDownloadUrl,
                    quality = "MP3",
                    thumbnailUrl = highResArtwork ?: rawArtwork.takeIf { it.isNotBlank() }
                )
            )

            if (!highResArtwork.isNullOrBlank()) {
                options.add(
                    MediaDownloadOption(
                        id = "sc_cover_art",
                        label = "Cover Album / Artwork (HD Foto)",
                        format = DownloadFormat.IMAGE,
                        url = highResArtwork,
                        quality = "HD 500x500",
                        thumbnailUrl = highResArtwork
                    )
                )
            }

            return Result.success(
                ExtractedMediaInfo(
                    platform = MediaPlatform.SOUNDCLOUD,
                    originalUrl = canonicalUrl,
                    title = trackTitle,
                    author = trackAuthor,
                    thumbnailUrl = highResArtwork ?: rawArtwork.takeIf { it.isNotBlank() },
                    options = options
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
