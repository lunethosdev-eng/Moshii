package com.pokke.music.data.youtube

import com.pokke.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Catálogo YouTube Music vía InnerTube.
 * Activar en Settings (`youtube_catalog_enabled`).
 * Sin PoToken: búsqueda + metadata; el stream se resuelve al reproducir.
 */
class YouTubeCatalogRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    // Public InnerTube key used by YouTube Music clients
    private val apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    suspend fun search(query: String, limit: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            // Prefer WEB_REMIX (more stable JSON shape for music search)
            searchWithClient(query, limit, clientName = "WEB_REMIX", clientVersion = "1.20241001.01.00", clientId = "67")
                .ifEmpty {
                    searchWithClient(query, limit, clientName = "ANDROID_MUSIC", clientVersion = "7.27.52", clientId = "21")
                }
        }
    }

    private fun searchWithClient(
        query: String,
        limit: Int,
        clientName: String,
        clientVersion: String,
        clientId: String
    ): List<Song> {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", clientName)
                        .put("clientVersion", clientVersion)
                        .put("hl", "es")
                        .put("gl", "US")
                )
            )
            .put("query", query)
            // Filter: songs
            .put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?key=$apiKey&prettyPrint=false")
            .header(
                "User-Agent",
                if (clientName == "ANDROID_MUSIC")
                    "com.google.android.apps.youtube.music/$clientVersion (Linux; U; Android 14)"
                else
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .header("X-YouTube-Client-Name", clientId)
            .header("X-YouTube-Client-Version", clientVersion)
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return emptyList()
            return parseSongs(text, limit)
        }
    }

    private fun parseSongs(json: String, limit: Int): List<Song> {
        val root = JSONObject(json)
        val out = ArrayList<Song>()
        fun walk(node: Any?) {
            if (out.size >= limit || node == null) return
            when (node) {
                is JSONObject -> {
                    when {
                        node.has("musicResponsiveListItemRenderer") ->
                            parseItem(node.getJSONObject("musicResponsiveListItemRenderer"))?.let { out.add(it) }
                        node.has("musicTwoRowItemRenderer") ->
                            parseTwoRow(node.getJSONObject("musicTwoRowItemRenderer"))?.let { out.add(it) }
                    }
                    node.keys().forEach { k -> walk(node.opt(k)) }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) walk(node.opt(i))
                }
            }
        }
        walk(root)
        return out.distinctBy { it.id }.take(limit)
    }

    private fun parseItem(item: JSONObject): Song? = runCatching {
        val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
            ?: findVideoId(item)
            ?: return null
        val flex = item.optJSONArray("flexColumns")
        val title = flex?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: "YouTube"
        val artist = flex?.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: "YouTube Music"
        val cover = extractThumb(item)
        Song(
            id = "yt_$videoId",
            title = title,
            artist = artist,
            album = "YouTube Music",
            coverUrl = cover ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            audioUrl = null,
            durationSeconds = 0,
            youtubeId = videoId
        )
    }.getOrNull()

    private fun parseTwoRow(item: JSONObject): Song? = runCatching {
        val videoId = findVideoId(item) ?: return null
        val title = item.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: "YouTube"
        val artist = item.optJSONObject("subtitle")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: "YouTube Music"
        val cover = extractThumb(item)
        Song(
            id = "yt_$videoId",
            title = title,
            artist = artist,
            album = "YouTube Music",
            coverUrl = cover ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            audioUrl = null,
            durationSeconds = 0,
            youtubeId = videoId
        )
    }.getOrNull()

    private fun extractThumb(item: JSONObject): String? {
        val thumbs = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: item.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
        if (thumbs == null || thumbs.length() == 0) return null
        return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
    }

    private fun findVideoId(o: JSONObject): String? {
        val m = Regex("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"").find(o.toString())
        return m?.groupValues?.getOrNull(1)
    }
}
