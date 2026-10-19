package com.example.hdify.domain.downloader

import com.example.hdify.R

enum class MediaPlatform(val displayName: String, val iconRes: Int) {
    TIKTOK("TikTok", R.drawable.ic_video),
    INSTAGRAM("Instagram", R.drawable.ic_image),
    TWITTER_X("X (Twitter)", R.drawable.ic_share),
    YOUTUBE("YouTube", R.drawable.ic_video),
    YOUTUBE_MUSIC("YouTube Music", R.drawable.ic_music),
    SOUNDCLOUD("SoundCloud", R.drawable.ic_music),
    DOUYIN("Douyin", R.drawable.ic_video),
    UNKNOWN("Link Media", R.drawable.ic_link);

    companion object {
        fun detectFromUrl(url: String): MediaPlatform {
            val lower = url.lowercase().trim()
            return when {
                lower.contains("music.youtube.com") -> YOUTUBE_MUSIC
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("soundcloud.com") || lower.contains("on.soundcloud.com") -> SOUNDCLOUD
                lower.contains("tiktok.com") || lower.contains("douyin.com/tiktok") -> TIKTOK
                lower.contains("douyin.com") || lower.contains("iesdouyin.com") -> DOUYIN
                lower.contains("instagram.com") || lower.contains("instagr.am") -> INSTAGRAM
                lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("t.co") -> TWITTER_X
                else -> UNKNOWN
            }
        }
    }
}
