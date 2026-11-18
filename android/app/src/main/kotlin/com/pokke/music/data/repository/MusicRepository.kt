package com.pokke.music.data.repository

import com.pokke.music.data.model.Album
import com.pokke.music.data.model.Artist
import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.UkiyoConfig
import com.pokke.music.data.remote.CoverUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Read-only catalog access for Ukiyo. All catalog data comes from the connected Supabase project. */
class MusicRepository {
    private val songSelect = "id,title,artist,album,cover_url,animated_cover_url,audio_url,duration_seconds,lyrics_text,lyrics_url,genre,youtube_id"

    suspend fun searchSongs(query: String, limit: Int = 40): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Escribe algo para buscar" }
            val pattern = "*${filterTerm(query)}*"
            val filter = "(title.ilike.$pattern,artist.ilike.$pattern,album.ilike.$pattern,genre.ilike.$pattern)"
            val body = request(UkiyoConfig.SONGS, mapOf(
                "select" to songSelect,
                "or" to filter,
                "order" to "created_at.desc",
                "limit" to limit.coerceIn(1, 100).toString()
            ))
            parseSongs(JSONArray(body))
        }
    }

    suspend fun getRecentSongs(limit: Int = 40): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request(UkiyoConfig.SONGS, mapOf(
                "select" to songSelect,
                "order" to "created_at.desc",
                "limit" to limit.coerceIn(1, 100).toString()
            ))
            parseSongs(JSONArray(body))
        }
    }

    suspend fun getSongById(id: String): Result<Song?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request(UkiyoConfig.SONGS, mapOf(
                "select" to songSelect,
                "id" to "eq.$id",
                "limit" to "1"
            ))
            val rows = JSONArray(body)
            if (rows.length() == 0) null else parseSong(rows.getJSONObject(0))
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 30): Result<List<Artist>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Escribe algo para buscar" }
            val body = request(UkiyoConfig.ARTISTS, mapOf(
                "select" to "id,name,image_url,bio",
                "name" to "ilike.*${filterTerm(query)}*",
                "order" to "name.asc",
                "limit" to limit.coerceIn(1, 100).toString()
            ))
            val rows = JSONArray(body)
            val fromArtistTable = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                Artist(
                    id = row.optString("id"),
                    name = row.optString("name"),
                    imageUrl = row.optString("image_url").nullable(),
                    bio = row.optString("bio").nullable()
                )
            }
            // The artists table is curated and smaller than the song catalog; merge names
            // found directly on songs so every artist with tracks remains searchable.
            val songRows = JSONArray(request(UkiyoConfig.SONGS, mapOf(
                "select" to "id,title,artist,album,cover_url,animated_cover_url,audio_url,duration_seconds,lyrics_text,lyrics_url,genre,youtube_id",
                "artist" to "ilike.*${filterTerm(query)}*",
                "limit" to limit.coerceIn(1, 100).toString()
            )))
            val fromSongs = (0 until songRows.length()).map { index ->
                val song = parseSong(songRows.getJSONObject(index))
                Artist(id = song.artist, name = song.artist, imageUrl = song.displayCoverUrl)
            }
            (fromArtistTable + fromSongs).distinctBy { it.name.trim().lowercase() }.take(limit.coerceIn(1, 100))
        }
    }

    /** Ukiyo currently stores album names on songs, not in a separate albums table. */
    suspend fun searchAlbums(query: String, limit: Int = 50): Result<List<Album>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Escribe algo para buscar" }
            val body = request(UkiyoConfig.SONGS, mapOf(
                "select" to songSelect,
                "album" to "ilike.*${filterTerm(query)}*",
                "order" to "album.asc",
                "limit" to limit.coerceIn(1, 100).toString()
            ))
            toAlbums(parseSongs(JSONArray(body)))
        }
    }

    suspend fun getSongsByArtist(artist: String, limit: Int = 100): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request(UkiyoConfig.SONGS, mapOf(
                "select" to songSelect,
                "artist" to "ilike.*${filterTerm(artist)}*",
                "order" to "album.asc,title.asc",
                "limit" to limit.coerceIn(1, 100).toString()
            ))
            parseSongs(JSONArray(body))
        }
    }

    suspend fun getSongsByAlbum(album: String, artist: String? = null): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf(
                "select" to songSelect,
                "album" to "eq.$album",
                "order" to "title.asc",
                "limit" to "100"
            )
            if (!artist.isNullOrBlank()) params["artist"] = "eq.$artist"
            parseSongs(JSONArray(request(UkiyoConfig.SONGS, params)))
        }
    }

    suspend fun getLyrics(song: Song): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            song.lyricsText?.takeIf(String::isNotBlank) ?: song.lyricsUrl
                ?.takeIf(String::isNotBlank)
                ?.let { requestExternalText(it) }
        }
    }

    private fun toAlbums(songs: List<Song>): List<Album> = songs
        .filter { !it.album.isNullOrBlank() }
        .groupBy { (it.album!!.trim().lowercase() to it.artist.trim().lowercase()) }
        .values.map { tracks ->
            val first = tracks.first()
            Album(
                name = first.album.orEmpty(),
                artist = first.artist,
                coverUrl = first.displayCoverUrl,
                songCount = tracks.size
            )
        }.sortedBy { it.name.lowercase() }

    private fun request(path: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val connection = (URL("${UkiyoConfig.SUPABASE_URL}$path?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("apikey", UkiyoConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${UkiyoConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
                throw IOException("Ukiyo respondió HTTP $status${detail.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun requestExternalText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "text/plain, text/*, */*")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("No se pudieron cargar las letras (HTTP $status)")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun filterTerm(value: String): String = value.trim().replace("*", " ").replace("%", " ")
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun String?.nullable(): String? = this?.takeIf { it.isNotBlank() && it != "null" }

    private fun parseSongs(rows: JSONArray): List<Song> = (0 until rows.length()).map { parseSong(rows.getJSONObject(it)) }

    private fun parseSong(row: JSONObject): Song {
        val youtubeId = row.optString("youtube_id").nullable()
        val cover = CoverUrls.best(row.optString("cover_url").nullable(), youtubeId, UkiyoConfig.SUPABASE_URL)
        val animated = CoverUrls.absolute(row.optString("animated_cover_url").nullable(), UkiyoConfig.SUPABASE_URL)
        val audioRaw = row.optString("audio_url").nullable()
        val audio = audioRaw?.let {
            if (it.startsWith("http") || it.startsWith("file") || it.startsWith("content")) it
            else CoverUrls.absolute(it, UkiyoConfig.SUPABASE_URL) ?: it
        }
        return Song(
            id = row.optString("id"),
            title = row.optString("title").ifBlank { "Sin título" },
            artist = row.optString("artist").ifBlank { "Artista desconocido" },
            album = row.optString("album").nullable(),
            coverUrl = cover,
            animatedCoverUrl = animated,
            audioUrl = audio,
            durationSeconds = row.optInt("duration_seconds", 0),
            lyricsText = row.optString("lyrics_text").nullable(),
            lyricsUrl = row.optString("lyrics_url").nullable(),
            genre = row.optString("genre").nullable(),
            youtubeId = youtubeId
        )
    }
}
