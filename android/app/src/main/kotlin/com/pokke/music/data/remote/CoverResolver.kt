package com.pokke.music.data.remote

import android.util.LruCache
import com.pokke.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverResolver {
    private val cache = LruCache<String, String>(120)
    fun cached(songId: String): String? = cache.get(songId)
    suspend fun resolve(song: Song): String? = withContext(Dispatchers.IO) {
        cached(song.id)?.let { return@withContext it }
        song.displayCoverUrl?.let {
            cache.put(song.id, it)
            return@withContext it
        }
        val resolved = CoverUrls.resolveBest(song.coverUrl, song.youtubeId, song.title, song.artist)
        if (!resolved.isNullOrBlank()) cache.put(song.id, resolved)
        resolved
    }
}
