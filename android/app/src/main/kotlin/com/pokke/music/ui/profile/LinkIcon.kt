package com.pokke.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Resolves a favicon / brand icon for any URL.
 * Uses Google's favicon service (sz=64) — works for almost any domain.
 * Common apps get a slightly higher-res clearbit/google fallback chain.
 */
fun faviconUrlFor(url: String, size: Int = 64): String? {
    val host = try {
        val u = if (url.startsWith("http")) url else "https://$url"
        java.net.URI(u).host?.removePrefix("www.")?.lowercase()
    } catch (_: Exception) {
        null
    } ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=$size"
}

fun hostLabel(url: String): String {
    return try {
        val u = if (url.startsWith("http")) url else "https://$url"
        java.net.URI(u).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}

/** Known display names for popular platforms (optional polish). */
fun platformLabel(url: String): String? {
    val h = hostLabel(url).lowercase()
    return when {
        h.contains("spotify") -> "Spotify"
        h.contains("youtube") || h == "youtu.be" -> "YouTube"
        h.contains("twitter") || h.contains("x.com") -> "X"
        h.contains("instagram") -> "Instagram"
        h.contains("tiktok") -> "TikTok"
        h.contains("discord") -> "Discord"
        h.contains("github") -> "GitHub"
        h.contains("twitch") -> "Twitch"
        h.contains("soundcloud") -> "SoundCloud"
        h.contains("pinterest") -> "Pinterest"
        h.contains("playstation") || h.contains("psn") -> "PlayStation"
        h.contains("steam") -> "Steam"
        h.contains("mangadex") -> "MangaDex"
        h.contains("myanimelist") || h == "mal.moe" -> "MyAnimeList"
        h.contains("anilist") -> "AniList"
        else -> null
    }
}

@Composable
fun LinkFavicon(
    url: String,
    size: Dp = 22.dp,
    tintFallback: Color = Color.White.copy(alpha = 0.7f),
    modifier: Modifier = Modifier
) {
    val iconUrl = remember(url) { faviconUrlFor(url, 128) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(size * 0.85f),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Default.Link, null, tint = tintFallback, modifier = Modifier.size(size * 0.7f))
        }
    }
}
