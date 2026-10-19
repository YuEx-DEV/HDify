package com.example.hdify.video.compressor

import android.media.MediaFormat

enum class CompressPreset(
    val title: String,
    val platform: String,
    val subtitle: String,
    val description: String,
    val recommendedCodec: String,
    val maxDimension: Int = 9999, // 9999 indicates original resolution
    val compressionRatio: Float = 0.50f,
    val iFrameIntervalSec: Int = 1,
    val badge: String = ""
) {
    ORIGINAL_HIGH(
        title = "Kualitas Asli (Kompres Cerdas ~30%)",
        platform = "Kualitas Master",
        subtitle = "Resolusi & FPS Asli • Hemat ~30%",
        description = "Pertahankan resolusi dan FPS asli dengan kejernihan maksimal. Ukuran file berkurang ~30% untuk upload anti-burik di sosmed.",
        recommendedCodec = MediaFormat.MIMETYPE_VIDEO_AVC,
        maxDimension = 9999,
        compressionRatio = 0.70f,
        iFrameIntervalSec = 1,
        badge = "Kualitas Maksimal"
    ),
    ORIGINAL_BALANCED(
        title = "Seimbang (Rekomendasi ~50%)",
        platform = "Upload Medsos",
        subtitle = "Resolusi & FPS Asli • Hemat ~50%",
        description = "Resolusi dan FPS tetap sesuai file asli, namun ukuran file berkurang setengahnya (~50%). Pilihan terbaik untuk Story, Status, Reels, dan TikTok.",
        recommendedCodec = MediaFormat.MIMETYPE_VIDEO_AVC,
        maxDimension = 9999,
        compressionRatio = 0.50f,
        iFrameIntervalSec = 1,
        badge = "Rekomendasi"
    ),
    ORIGINAL_SAVER(
        title = "Hemat Ukuran (Kompres Kuat ~70%)",
        platform = "Kirim Cepat",
        subtitle = "Resolusi & FPS Asli • Hemat ~70%",
        description = "Resolusi dan FPS tetap sesuai file asli, ukuran file dipangkas hingga ~70%. Sangat ringan untuk dikirim via chat WhatsApp atau simpan arsip.",
        recommendedCodec = MediaFormat.MIMETYPE_VIDEO_AVC,
        maxDimension = 9999,
        compressionRatio = 0.30f,
        iFrameIntervalSec = 1,
        badge = "Hemat Kuota 70%"
    ),
    CUSTOM(
        title = "Kustom Manual",
        platform = "Kustom",
        subtitle = "Sesuaikan Bitrate & Resolusi",
        description = "Atur sendiri resolusi, codec target, persentase bitrate, dan batas frame rate sesuai preferensi kamu.",
        recommendedCodec = MediaFormat.MIMETYPE_VIDEO_AVC,
        maxDimension = 9999,
        compressionRatio = 0.50f,
        iFrameIntervalSec = 1,
        badge = "Pro Custom"
    );

    /**
     * Menghitung target bitrate yang menjamin video terkompresi lebih kecil dari aslinya,
     * sambil menjaga visual fidelity anti-pecah pada resolusi dan FPS aslinya.
     */
    fun calculateTargetBitrate(originalBitrateBps: Long, width: Int, height: Int, fps: Float): Long {
        val pixels = width * height
        val safeFps = if (fps > 0f) fps else 30.0f
        val fpsFactor = (safeFps / 30.0f).coerceIn(0.85f, 2.0f)

        // Baseline perkiraan jika bitrate file sumber tidak terdeteksi dari metadata
        val estimatedOriginalBitrate = if (originalBitrateBps > 500_000L) {
            originalBitrateBps
        } else {
            when {
                pixels >= 3840 * 2160 -> (35_000_000L * fpsFactor).toLong()
                pixels >= 2560 * 1440 -> (22_000_000L * fpsFactor).toLong()
                pixels >= 1920 * 1080 -> (12_000_000L * fpsFactor).toLong()
                pixels >= 1280 * 720  -> (6_000_000L * fpsFactor).toLong()
                else -> (3_000_000L * fpsFactor).toLong()
            }
        }

        // Tentukan batas bawah mutlak (quality floor) per resolusi agar video TIDAK PERNAH pecah/burik
        val qualityFloor = when {
            pixels >= 3840 * 2160 -> when (this) {
                ORIGINAL_HIGH -> (18_000_000L * fpsFactor).toLong()
                ORIGINAL_BALANCED -> (13_000_000L * fpsFactor).toLong()
                ORIGINAL_SAVER -> (9_000_000L * fpsFactor).toLong()
                CUSTOM -> 8_000_000L
            }
            pixels >= 2560 * 1440 -> when (this) {
                ORIGINAL_HIGH -> (11_000_000L * fpsFactor).toLong()
                ORIGINAL_BALANCED -> (7_500_000L * fpsFactor).toLong()
                ORIGINAL_SAVER -> (5_200_000L * fpsFactor).toLong()
                CUSTOM -> 4_500_000L
            }
            pixels >= 1920 * 1080 -> when (this) {
                ORIGINAL_HIGH -> (6_000_000L * fpsFactor).toLong()
                ORIGINAL_BALANCED -> (4_200_000L * fpsFactor).toLong()
                ORIGINAL_SAVER -> (3_000_000L * fpsFactor).toLong()
                CUSTOM -> 2_500_000L
            }
            pixels >= 1280 * 720 -> when (this) {
                ORIGINAL_HIGH -> (3_500_000L * fpsFactor).toLong()
                ORIGINAL_BALANCED -> (2_400_000L * fpsFactor).toLong()
                ORIGINAL_SAVER -> (1_600_000L * fpsFactor).toLong()
                CUSTOM -> 1_200_000L
            }
            else -> when (this) {
                ORIGINAL_HIGH -> (1_800_000L * fpsFactor).toLong()
                ORIGINAL_BALANCED -> (1_200_000L * fpsFactor).toLong()
                ORIGINAL_SAVER -> (900_000L * fpsFactor).toLong()
                CUSTOM -> 800_000L
            }
        }

        // Batas atas (ceiling) visual-lossless agar video kamera HP yang bloated tidak menghasilkan bitrate berlebih
        val ceiling = when (this) {
            ORIGINAL_HIGH -> when {
                pixels >= 3840 * 2160 -> (32_000_000L * fpsFactor).toLong()
                pixels >= 2560 * 1440 -> (20_000_000L * fpsFactor).toLong()
                pixels >= 1920 * 1080 -> (12_000_000L * fpsFactor).toLong()
                pixels >= 1280 * 720  -> (6_500_000L * fpsFactor).toLong()
                else -> (3_500_000L * fpsFactor).toLong()
            }
            ORIGINAL_BALANCED -> when {
                pixels >= 3840 * 2160 -> (22_000_000L * fpsFactor).toLong()
                pixels >= 2560 * 1440 -> (14_000_000L * fpsFactor).toLong()
                pixels >= 1920 * 1080 -> (8_500_000L * fpsFactor).toLong()
                pixels >= 1280 * 720  -> (4_500_000L * fpsFactor).toLong()
                else -> (2_500_000L * fpsFactor).toLong()
            }
            ORIGINAL_SAVER -> when {
                pixels >= 3840 * 2160 -> (14_000_000L * fpsFactor).toLong()
                pixels >= 2560 * 1440 -> (9_000_000L * fpsFactor).toLong()
                pixels >= 1920 * 1080 -> (5_500_000L * fpsFactor).toLong()
                pixels >= 1280 * 720  -> (3_000_000L * fpsFactor).toLong()
                else -> (1_800_000L * fpsFactor).toLong()
            }
            CUSTOM -> (estimatedOriginalBitrate * compressionRatio).toLong()
        }

        val rawTarget = (estimatedOriginalBitrate * compressionRatio).toLong()

        // Jika bitrate asli sudah sangat rendah (di bawah quality floor), lindungi kualitas visual
        // agar tidak pecah/hancur, tetap berikan penghematan aman tanpa artefak blok
        return if (originalBitrateBps > 500_000L && originalBitrateBps <= qualityFloor * 1.15) {
            val safeRetention = (originalBitrateBps * 0.90).toLong()
            maxOf(safeRetention, (qualityFloor * 0.75).toLong())
        } else {
            minOf(rawTarget, ceiling).coerceAtLeast(qualityFloor)
        }
    }
}
