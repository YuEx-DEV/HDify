package com.example.hdify.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdify.domain.downloader.DownloadFormat
import com.example.hdify.domain.downloader.DownloadProgress
import com.example.hdify.domain.downloader.ExtractedMediaInfo
import com.example.hdify.domain.downloader.MediaDownloadOption
import com.example.hdify.domain.downloader.MediaPlatform
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tikWmExtractor: TikWmExtractor,
    private val twitterExtractor: TwitterExtractor,
    private val instagramExtractor: InstagramExtractor,
    private val youtubeExtractor: YouTubeExtractor,
    private val soundCloudExtractor: SoundCloudExtractor
) {

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extractMedia(url: String): Result<ExtractedMediaInfo> {
        val platform = MediaPlatform.detectFromUrl(url)
        HdLogger.i("MediaDownloaderRepo", "Extracting url: $url, detected platform: ${platform.name}")

        return when (platform) {
            MediaPlatform.TIKTOK, MediaPlatform.DOUYIN -> {
                tikWmExtractor.extract(url, platform)
            }
            MediaPlatform.TWITTER_X -> {
                twitterExtractor.extract(url)
            }
            MediaPlatform.INSTAGRAM -> {
                instagramExtractor.extract(url)
            }
            MediaPlatform.YOUTUBE, MediaPlatform.YOUTUBE_MUSIC -> {
                youtubeExtractor.extract(url, platform)
            }
            MediaPlatform.SOUNDCLOUD -> {
                soundCloudExtractor.extract(url)
            }
            MediaPlatform.UNKNOWN -> {
                // Try YouTube first if url looks like youtube
                if (url.contains("youtu", ignoreCase = true)) {
                    val yt = youtubeExtractor.extract(url)
                    if (yt.isSuccess) return yt
                }
                if (url.contains("sound", ignoreCase = true)) {
                    val sc = soundCloudExtractor.extract(url)
                    if (sc.isSuccess) return sc
                }

                // Try TikWM first, then Twitter, then Instagram as fallback
                val tikWmResult = tikWmExtractor.extract(url, MediaPlatform.UNKNOWN)
                if (tikWmResult.isSuccess) return tikWmResult

                val twResult = twitterExtractor.extract(url)
                if (twResult.isSuccess) return twResult

                instagramExtractor.extract(url)
            }
        }
    }

    fun downloadMedia(
        option: MediaDownloadOption,
        filenamePrefix: String = "media"
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Progress(0, 0, 0))

        val extension = when (option.format) {
            DownloadFormat.VIDEO -> "mp4"
            DownloadFormat.AUDIO -> "mp3"
            DownloadFormat.IMAGE -> "jpg"
        }

        val mimeType = when (option.format) {
            DownloadFormat.VIDEO -> "video/mp4"
            DownloadFormat.AUDIO -> "audio/mpeg"
            DownloadFormat.IMAGE -> "image/jpeg"
        }

        val relativeDir = when (option.format) {
            DownloadFormat.VIDEO -> Environment.DIRECTORY_MOVIES + "/HDify"
            DownloadFormat.AUDIO -> Environment.DIRECTORY_MUSIC + "/HDify"
            DownloadFormat.IMAGE -> Environment.DIRECTORY_PICTURES + "/HDify"
        }

        val contentUriCollection = when (option.format) {
            DownloadFormat.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            DownloadFormat.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            DownloadFormat.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val cleanPrefix = filenamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val displayName = "HDify_${cleanPrefix}_${System.currentTimeMillis()}.$extension"

        val tempFile = java.io.File(context.cacheDir, "temp_dl_${System.currentTimeMillis()}.$extension")
        val resolver = context.contentResolver
        var itemUri: Uri? = null
        var success = false

        try {
            val referer = when {
                option.url.contains("tiktok") || option.url.contains("tikwm") -> "https://www.tiktok.com/"
                option.url.contains("twitter") || option.url.contains("twimg") -> "https://twitter.com/"
                option.url.contains("instagram") || option.url.contains("cdninstagram") -> "https://www.instagram.com/"
                option.url.contains("sndcdn") || option.url.contains("soundcloud") -> "https://soundcloud.com/"
                option.url.contains("googlevideo") || option.url.contains("youtube") -> "https://www.youtube.com/"
                else -> null
            }

            val reqBuilder = Request.Builder()
                .url(option.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Encoding", "identity")

            if (referer != null) {
                reqBuilder.header("Referer", referer)
            }

            val request = reqBuilder.build()
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress.Error("Download gagal (HTTP ${response.code})"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadProgress.Error("Respon server kosong saat mengunduh file."))
                return@flow
            }

            // Verify content-type to avoid saving HTML error/captcha pages as corrupt video/audio
            val contentType = response.header("Content-Type").orEmpty()
            if ((option.format == DownloadFormat.VIDEO || option.format == DownloadFormat.AUDIO) &&
                contentType.contains("text/html", ignoreCase = true)) {
                emit(DownloadProgress.Error("Gagal: Link download kedaluwarsa atau dialihkan ke halaman web."))
                return@flow
            }

            var totalBytes = body.contentLength()
            if (totalBytes <= 0L && option.sizeBytes != null && option.sizeBytes > 0L) {
                totalBytes = option.sizeBytes
            }

            var downloadedBytes = 0L
            val inputStream: InputStream = body.byteStream()

            // Stage 1: Download to temp file first to ensure integrity
            java.io.FileOutputStream(tempFile).use { tempOut ->
                inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastPercent = -1
                    var lastEmitMs = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        tempOut.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        // Emit every percent change OR every 200ms so live bytes update continuously
                        if (percent != lastPercent || (now - lastEmitMs >= 200L)) {
                            lastPercent = percent
                            lastEmitMs = now
                            emit(DownloadProgress.Progress(downloadedBytes, totalBytes, percent))
                        }
                    }
                    tempOut.flush()
                }
            }

            // Stage 2: Integrity Verification
            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                throw java.io.IOException("Download terputus: Hanya terunduh $downloadedBytes dari $totalBytes bytes")
            }
            if (option.format == DownloadFormat.VIDEO && downloadedBytes < 10 * 1024) {
                throw java.io.IOException("File video rusak atau tidak lengkap ($downloadedBytes bytes)")
            }

            // Stage 3: Copy verified temp file to MediaStore
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertedUri = resolver.insert(contentUriCollection, values)
                ?: throw java.io.IOException("Gagal membuat entri file di MediaStore.")
            itemUri = insertedUri

            val outputStream: OutputStream? = resolver.openOutputStream(insertedUri)
            if (outputStream == null) {
                throw java.io.IOException("Gagal membuka stream penyimpanan MediaStore.")
            }

            outputStream.use { out ->
                java.io.FileInputStream(tempFile).use { fileIn ->
                    fileIn.copyTo(out)
                }
                out.flush()
            }

            // Mark file as complete in MediaStore (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(insertedUri, values, null, null)
            }

            success = true
            HdLogger.i("MediaDownloaderRepo", "Download finished: $displayName, size: $downloadedBytes bytes, Uri: $insertedUri")
            emit(DownloadProgress.Success(insertedUri, displayName, option.format))

        } catch (t: Throwable) {
            HdLogger.e("MediaDownloaderRepo", null, "HD-DL-004", "Download stream failed for ${option.url}", t)
            emit(DownloadProgress.Error(t.localizedMessage ?: "Terjadi kesalahan saat mengunduh file."))
        } finally {
            try { tempFile.delete() } catch (_: Throwable) {}
            if (!success && itemUri != null) {
                try {
                    resolver.delete(itemUri, null, null)
                } catch (_: Throwable) {}
            }
        }
    }.flowOn(Dispatchers.IO)
}
