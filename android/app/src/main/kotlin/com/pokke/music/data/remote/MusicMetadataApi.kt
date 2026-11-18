package com.pokke.music.data.remote

import com.pokke.music.data.model.LyricLine
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object MusicMetadataApi {
    data class ArtistInfo(val name: String, val image: String?, val description: String?, val albums: Int?)
    data class LyricsResult(val lines: List<LyricLine>, val plain: String?)

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000; readTimeout = 9000; requestMethod = "GET"
            setRequestProperty("User-Agent", "Pokke/1.0 (Android music player)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    fun lyrics(title: String, artist: String, album: String?, durationSeconds: Int): LyricsResult {
        val params = buildList {
            add("track_name=" + enc(title)); add("artist_name=" + enc(artist))
            if (!album.isNullOrBlank()) add("album_name=" + enc(album))
            if (durationSeconds in 1..3600) add("duration=$durationSeconds")
        }.joinToString("&")
        val json = runCatching { JSONObject(get("https://lrclib.net/api/get?$params")) }.getOrElse {
            val results = JSONArray(get("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"))
            if (results.length() == 0) throw it
            results.getJSONObject(0)
        }
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val lines = synced?.lineSequence()?.mapNotNull { line ->
            val match = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)").find(line) ?: return@mapNotNull null
            val min = match.groupValues[1].toLong(); val sec = match.groupValues[2].toLong()
            val ms = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            LyricLine((min * 60 + sec) * 1000 + ms, match.groupValues[4])
        }?.toList().orEmpty()
        return LyricsResult(lines, plain)
    }

    fun artistInfo(name: String): ArtistInfo {
        val q = enc(name)
        val search = JSONObject(get("https://api.deezer.com/search/artist?q=$q")).optJSONArray("data") ?: JSONArray()
        val first = if (search.length() > 0) search.getJSONObject(0) else null
        val artistName = first?.optString("name")?.takeIf { it.isNotBlank() } ?: name
        val image = first?.optString("picture_xl")?.takeIf { it.isNotBlank() && it != "null" }
            ?: first?.optString("picture_big")?.takeIf { it.isNotBlank() && it != "null" }
        val id = first?.optLong("id", -1L) ?: -1L
        val albumCount = if (id > 0) runCatching { JSONObject(get("https://api.deezer.com/artist/$id")).optInt("nb_album") }.getOrNull() else null
        val wiki = runCatching {
            val title = enc(artistName.replace(' ', '_'))
            JSONObject(get("https://en.wikipedia.org/api/rest_v1/page/summary/$title")).optString("extract").takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
        return ArtistInfo(artistName, image, wiki, albumCount)
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
