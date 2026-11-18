package com.pokke.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pokke.music.data.model.Playlist
import com.pokke.music.data.model.Song
import com.pokke.music.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persistent on-device library. Downloads use Android DownloadManager, not a temporary cache. */
class LocalLibraryStore(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private val HISTORY = stringPreferencesKey("library_history_v1")
        private val DOWNLOADS = stringPreferencesKey("library_downloads_v1")
        private val DOWNLOAD_IDS = stringPreferencesKey("library_download_ids_v1")
        private val DOWNLOAD_PATHS = stringPreferencesKey("library_download_paths_v1")
        private val PLAYLISTS = stringPreferencesKey("library_playlists_v1")
    }

    val history: Flow<List<Song>> = context.dataStore.data.map { parseSongs(it[HISTORY]) }
    private val downloadRefresh = flow {
        while (true) { emit(Unit); delay(2_000L) }
    }
    val downloads: Flow<List<Song>> = combine(context.dataStore.data, downloadRefresh) { prefs, _ ->
        val ids = runCatching { JSONObject(prefs[DOWNLOAD_IDS] ?: "{}") }.getOrDefault(JSONObject())
        val paths = runCatching { JSONObject(prefs[DOWNLOAD_PATHS] ?: "{}") }.getOrDefault(JSONObject())
        parseSongs(prefs[DOWNLOADS]).mapNotNull { song ->
            val id = ids.optLong(song.id, -1L)
            val path = paths.optString(song.id).takeIf { it.isNotBlank() }
            if (id > 0L && path != null && File(path).isFile && File(path).length() > 0L) song.copy(audioUrl = path) else null
        }
    }
    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { parsePlaylists(it[PLAYLISTS]) }

    suspend fun recordPlayed(song: Song) {
        context.dataStore.edit { prefs ->
            val original = parseSongs(prefs[DOWNLOADS]).firstOrNull { it.id == song.id } ?: song
            val next = (listOf(original) + parseSongs(prefs[HISTORY]).filterNot { it.id == song.id }).take(100)
            prefs[HISTORY] = songsJson(next)
        }
    }

    suspend fun download(song: Song): Long {
        val resolvedSong = if (!song.youtubeId.isNullOrBlank() || !song.audioUrl.isNullOrBlank()) song
            else song
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: error("Almacenamiento no disponible")
        if (!dir.exists()) dir.mkdirs()
        val safeName = song.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70).ifBlank { "track" }
        val extension = if (!resolvedSong.youtubeId.isNullOrBlank()) "m4a" else {
            val candidate = runCatching { Uri.parse(resolvedSong.audioUrl).lastPathSegment.orEmpty().substringAfterLast('.', "mp3") }.getOrDefault("mp3").lowercase()
            candidate.takeIf { it in setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "webm") } ?: "mp3"
        }
        val target = File(dir, "${song.id.hashCode()}_$safeName.$extension")
        val taskId = DownloadQueueManager.get(context).enqueue(resolvedSong, target)
        context.dataStore.edit { prefs ->
            val current = parseSongs(prefs[DOWNLOADS]).filterNot { it.id == song.id }
            prefs[DOWNLOADS] = songsJson(listOf(resolvedSong) + current)
            val ids = runCatching { JSONObject(prefs[DOWNLOAD_IDS] ?: "{}") }.getOrDefault(JSONObject())
            ids.put(song.id, taskId)
            prefs[DOWNLOAD_IDS] = ids.toString()
            val paths = runCatching { JSONObject(prefs[DOWNLOAD_PATHS] ?: "{}") }.getOrDefault(JSONObject())
            paths.put(song.id, target.absolutePath)
            prefs[DOWNLOAD_PATHS] = paths.toString()
        }
        return taskId
    }

    suspend fun removeDownload(song: Song) {
        val prefsNow = context.dataStore.data.first()
        val ids = runCatching { JSONObject(prefsNow[DOWNLOAD_IDS] ?: "{}") }.getOrDefault(JSONObject())
        val downloadId = ids.optLong(song.id, -1L)
        if (downloadId > 0L) {
            val engine = DownloadQueueManager.get(context)
            if (engine.tasks.value.containsKey(downloadId)) engine.cancel(downloadId)
            else runCatching { dm.remove(downloadId) } // migration: older DownloadManager-backed entries
        }
        val paths = runCatching { JSONObject(prefsNow[DOWNLOAD_PATHS] ?: "{}") }.getOrDefault(JSONObject())
        paths.optString(song.id).takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        context.dataStore.edit { prefs ->
            prefs[DOWNLOADS] = songsJson(parseSongs(prefs[DOWNLOADS]).filterNot { it.id == song.id })
            ids.remove(song.id)
            paths.remove(song.id)
            prefs[DOWNLOAD_IDS] = ids.toString()
            prefs[DOWNLOAD_PATHS] = paths.toString()
        }
    }

    private fun isDownloadComplete(id: Long): Boolean = runCatching {
        dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
    }.getOrDefault(false)

    suspend fun createPlaylist(name: String, description: String = "") {
        require(name.isNotBlank())
        context.dataStore.edit { prefs ->
            val current = JSONArray(prefs[PLAYLISTS] ?: "[]")
            current.put(JSONObject().put("id", UUID.randomUUID().toString()).put("name", name.trim()).put("description", description).put("songs", JSONArray()))
            prefs[PLAYLISTS] = current.toString()
        }
    }

    suspend fun deletePlaylist(id: String) {
        context.dataStore.edit { prefs ->
            val kept = JSONArray()
            parsePlaylistObjects(prefs[PLAYLISTS]).filterNot { it.optString("id") == id }.forEach { kept.put(it) }
            prefs[PLAYLISTS] = kept.toString()
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        context.dataStore.edit { prefs ->
            val root = parsePlaylistObjects(prefs[PLAYLISTS])
            val playlist = root.firstOrNull { it.optString("id") == playlistId } ?: return@edit
            val songs = playlist.optJSONArray("songs") ?: JSONArray()
            if ((0 until songs.length()).none { songs.optJSONObject(it)?.optString("id") == song.id }) songs.put(songJson(song))
            playlist.put("songs", songs)
            prefs[PLAYLISTS] = root.toString()
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        context.dataStore.edit { prefs ->
            val root = parsePlaylistObjects(prefs[PLAYLISTS])
            val playlist = root.firstOrNull { it.optString("id") == playlistId } ?: return@edit
            val old = playlist.optJSONArray("songs") ?: JSONArray()
            val kept = JSONArray()
            for (index in 0 until old.length()) {
                val item = old.optJSONObject(index) ?: continue
                if (item.optString("id") != songId) kept.put(item)
            }
            playlist.put("songs", kept)
            prefs[PLAYLISTS] = root.toString()
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val raw = context.dataStore.data.map { it[PLAYLISTS] }.let { it.first() }
        val playlist = parsePlaylistObjects(raw).firstOrNull { it.optString("id") == playlistId } ?: return emptyList()
        return parseSongs(playlist.optJSONArray("songs")?.toString())
    }

    private fun parseSongs(raw: String?): List<Song> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseSong) }
    }.getOrDefault(emptyList())

    private fun parseSong(o: JSONObject) = Song(
        id = o.optString("id"), title = o.optString("title"), artist = o.optString("artist"),
        album = o.optString("album").takeIf(String::isNotBlank), coverUrl = o.optString("coverUrl").takeIf(String::isNotBlank),
        animatedCoverUrl = o.optString("animatedCoverUrl").takeIf(String::isNotBlank), audioUrl = o.optString("audioUrl").takeIf(String::isNotBlank),
        durationSeconds = o.optInt("durationSeconds"), lyricsText = o.optString("lyricsText").takeIf(String::isNotBlank),
        lyricsUrl = o.optString("lyricsUrl").takeIf(String::isNotBlank), genre = o.optString("genre").takeIf(String::isNotBlank),
        youtubeId = o.optString("youtubeId").takeIf(String::isNotBlank)
    )

    private fun songJson(s: Song) = JSONObject().apply {
        put("id", s.id); put("title", s.title); put("artist", s.artist); put("album", s.album)
        put("coverUrl", s.coverUrl); put("animatedCoverUrl", s.animatedCoverUrl); put("audioUrl", s.audioUrl)
        put("durationSeconds", s.durationSeconds); put("lyricsText", s.lyricsText); put("lyricsUrl", s.lyricsUrl)
        put("genre", s.genre); put("youtubeId", s.youtubeId)
    }
    private fun songsJson(songs: List<Song>) = JSONArray().apply { songs.forEach { put(songJson(it)) } }.toString()
    private fun parsePlaylistObjects(raw: String?) = runCatching {
        val a = JSONArray(raw ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun playlistsJson(playlists: List<Playlist>) = JSONArray().apply {
        playlists.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("description", p.description).put("songs", JSONArray())) }
    }.toString()
    private fun parsePlaylists(raw: String?) = parsePlaylistObjects(raw).map { p ->
        val songs = p.optJSONArray("songs")
        Playlist(p.optString("id"), p.optString("name"), description = p.optString("description"), songCount = songs?.length() ?: 0)
    }
}
