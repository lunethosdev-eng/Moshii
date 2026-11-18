package com.pokke.music.data.repository

import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.MusicSourcesConfig
import com.pokke.music.data.remote.UkiyoConfig
import com.pokke.music.data.remote.CoverUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Queries all healthy music sources in parallel and merges results.
 * Auto-switch: failed sources are marked unhealthy for 60s and skipped.
 * If a source reports near softLimit capacity, prefer others for writes.
 */
class MultiMusicRepository {
    private val songSelect =
        "id,title,artist,album,cover_url,animated_cover_url,audio_url,duration_seconds,lyrics_text,lyrics_url,genre,youtube_id"

    
    /** searchAll + optional YouTube Music catalog (Settings toggle). */
    suspend fun searchAll(
        query: String,
        limit: Int = 40,
        includeYoutube: Boolean
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = searchAll(query, limit).getOrDefault(emptyList())
            if (!includeYoutube) return@runCatching base
            val yt = com.pokke.music.data.youtube.YouTubeCatalogRepository()
                .search(query, limit = limit / 2)
                .getOrDefault(emptyList())
            (base + yt).distinctBy { it.id.ifBlank { it.title + "|" + it.artist } }.take(limit)
        }
    }

    suspend fun searchAll(query: String, limit: Int = 40): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val sources = MusicSourcesConfig.enabled().ifEmpty {
                listOf(
                    com.pokke.music.data.remote.MusicSource(
                        "ukiyo", "ukiyo", "Ukiyo", UkiyoConfig.SUPABASE_URL, UkiyoConfig.SUPABASE_PUBLISHABLE_KEY, true, true
                    )
                )
            }
            coroutineScope {
                val jobs = sources.map { src ->
                    async {
                        try {
                            searchOne(src.id, src.supabaseUrl, src.publishableKey, query, limit)
                        } catch (e: Exception) {
                            MusicSourcesConfig.markUnhealthy(src.id)
                            emptyList()
                        }
                    }
                }
                jobs.awaitAll().flatten().distinctBy { it.id.ifBlank { it.title + "|" + it.artist } }.take(limit)
            }
        }
    }

    private fun searchOne(sourceId: String, baseUrl: String, key: String, query: String, limit: Int): List<Song> {
        val pattern = "*${query.trim()}*"
        val filter = "(title.ilike.$pattern,artist.ilike.$pattern,album.ilike.$pattern)"
        val params = "select=$songSelect&or=$filter&order=created_at.desc&limit=$limit"
        val url = "$baseUrl/rest/v1/songs?$params"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                MusicSourcesConfig.markUnhealthy(sourceId)
                return emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                parseSongRow(o, baseUrl)
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun getRecentAll(limit: Int = 40): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val sources = MusicSourcesConfig.enabled().ifEmpty {
                listOf(
                    com.pokke.music.data.remote.MusicSource(
                        "ukiyo", "ukiyo", "Ukiyo",
                        UkiyoConfig.SUPABASE_URL, UkiyoConfig.SUPABASE_PUBLISHABLE_KEY, true, true
                    )
                )
            }
            coroutineScope {
                val jobs = sources.map { src ->
                    async {
                        try {
                            recentOne(src.id, src.supabaseUrl, src.publishableKey, limit)
                        } catch (e: Exception) {
                            MusicSourcesConfig.markUnhealthy(src.id)
                            emptyList()
                        }
                    }
                }
                jobs.awaitAll().flatten().distinctBy { it.id.ifBlank { it.title + "|" + it.artist } }.take(limit)
            }
        }
    }

    private fun recentOne(sourceId: String, baseUrl: String, key: String, limit: Int): List<Song> {
        val params = "select=$songSelect&order=created_at.desc&limit=$limit"
        val url = "$baseUrl/rest/v1/songs?$params"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                MusicSourcesConfig.markUnhealthy(sourceId)
                return emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                parseSongRow(o, baseUrl)
            }
        } finally {
            conn.disconnect()
        }
    }


    private fun parseSongRow(o: org.json.JSONObject, baseUrl: String): Song {
        val yt = o.optString("youtube_id").takeIf { it.isNotBlank() && it != "null" }
        val cover = CoverUrls.best(
            o.optString("cover_url").takeIf { it.isNotBlank() && it != "null" },
            yt,
            baseUrl
        )
        val animated = CoverUrls.absolute(
            o.optString("animated_cover_url").takeIf { it.isNotBlank() && it != "null" },
            baseUrl
        )
        val audio = o.optString("audio_url").takeIf { it.isNotBlank() && it != "null" }?.let { raw ->
            if (raw.startsWith("http") || raw.startsWith("file") || raw.startsWith("content")) raw
            else CoverUrls.absolute(raw, baseUrl) ?: raw
        }
        return Song(
            id = o.optString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album").takeIf { it.isNotBlank() && it != "null" },
            coverUrl = cover,
            animatedCoverUrl = animated,
            audioUrl = audio,
            durationSeconds = o.optInt("duration_seconds", 0),
            lyricsText = o.optString("lyrics_text").takeIf { it.isNotBlank() && it != "null" },
            lyricsUrl = o.optString("lyrics_url").takeIf { it.isNotBlank() && it != "null" },
            genre = o.optString("genre").takeIf { it.isNotBlank() && it != "null" },
            youtubeId = yt
        )
    }
}
