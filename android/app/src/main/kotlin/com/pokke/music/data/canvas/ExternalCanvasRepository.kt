package com.pokke.music.data.canvas

import com.music.bitchord.data.canvas.CanvasArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public motion-artwork sources ported from the BitChord connector bundle.
 * Spotify is handled separately by [SpotifyCanvasProvider] because it requires
 * an explicitly connected Spotify Web session and a short-lived bearer token.
 */
object ExternalCanvasRepository {
    private const val CACHE_TTL_MS = 3L * 60L * 60L * 1000L
    private const val MISS_TTL_MS = 15L * 60L * 1000L

    private data class Entry(val artwork: CanvasArtwork?, val fetchedAt: Long)
    private val cache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 256
    }

    suspend fun find(title: String, artist: String, album: String?): CanvasArtwork? =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) return@withContext null
            val key = listOf(title, artist, album.orEmpty()).joinToString("|") {
                it.trim().lowercase().replace(Regex("\\s+"), " ")
            }
            val now = System.currentTimeMillis()
            val cached = synchronized(cache) { cache[key] }
            if (cached != null) {
                val ttl = if (cached.artwork == null) MISS_TTL_MS else CACHE_TTL_MS
                if (now - cached.fetchedAt < ttl) return@withContext cached.artwork
            }

            // Each provider independently validates its catalogue match. The
            // final check is deliberately repeated here: a wrong Canvas is
            // more noticeable than falling back to the static cover.
            val match = runCatching { AppleMusicCanvas.search(title, artist, album) }.getOrNull()
                ?.takeIf { it.matches(title, artist, album) }
                ?: runCatching { TidalCanvas.search(title, artist, album) }.getOrNull()
                    ?.takeIf { it.matches(title, artist, album) }
                ?: runCatching { CommunityCanvas.search(title, artist, album) }.getOrNull()
                    ?.takeIf { it.matches(title, artist, album) }
            synchronized(cache) { cache[key] = Entry(match, now) }
            match
        }

    fun clearCache() = synchronized(cache) { cache.clear() }
}
