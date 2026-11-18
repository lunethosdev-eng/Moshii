package com.pokke.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight MangaDex API client (public endpoints, no auth).
 * Docs: https://api.mangadex.org/docs/
 */
object MangaDexApi {
    private const val BASE = "https://api.mangadex.org"

    data class MangaItem(
        val id: String,
        val title: String,
        val coverUrl: String?,
        val description: String?,
        val year: Int?,
        val status: String?
    )

    suspend fun searchManga(query: String, limit: Int = 20): Result<List<MangaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Escribe un título de manga" }
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE/manga?title=$q&limit=${limit.coerceIn(1, 32)}&includes[]=cover_art&order[relevance]=desc&contentRating[]=safe&contentRating[]=suggestive"
            val json = JSONObject(httpGet(url))
            val data = json.optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map { i ->
                val item = data.getJSONObject(i)
                val id = item.getString("id")
                val attrs = item.getJSONObject("attributes")
                val titleObj = attrs.optJSONObject("title")
                val title = titleObj?.optString("en")
                    ?.takeIf { it.isNotBlank() }
                    ?: titleObj?.keys()?.asSequence()?.firstOrNull()?.let { titleObj.optString(it) }
                    ?: "Sin título"
                val descObj = attrs.optJSONObject("description")
                val desc = descObj?.optString("en")?.takeIf { it.isNotBlank() }
                    ?: descObj?.keys()?.asSequence()?.firstOrNull()?.let { descObj.optString(it) }
                val year = if (attrs.has("year") && !attrs.isNull("year")) attrs.optInt("year") else null
                val status = attrs.optString("status").takeIf { it.isNotBlank() }
                val coverFile = findCoverFileName(item.optJSONArray("relationships"))
                val coverUrl = coverFile?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }
                MangaItem(id, title, coverUrl, desc?.take(280), year, status)
            }
        }
    }

    suspend fun getManga(id: String): Result<MangaItem?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/manga/$id?includes[]=cover_art"
            val json = JSONObject(httpGet(url))
            val item = json.optJSONObject("data") ?: return@runCatching null
            val attrs = item.getJSONObject("attributes")
            val titleObj = attrs.optJSONObject("title")
            val title = titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                ?: titleObj?.keys()?.asSequence()?.firstOrNull()?.let { titleObj.optString(it) }
                ?: "Sin título"
            val coverFile = findCoverFileName(item.optJSONArray("relationships"))
            val coverUrl = coverFile?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }
            MangaItem(id, title, coverUrl, null, null, attrs.optString("status").takeIf { it.isNotBlank() })
        }
    }

    private fun findCoverFileName(rels: JSONArray?): String? {
        if (rels == null) return null
        for (i in 0 until rels.length()) {
            val r = rels.getJSONObject(i)
            if (r.optString("type") == "cover_art") {
                return r.optJSONObject("attributes")?.optString("fileName")?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Pokke/1.0 (profile customization)")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw java.io.IOException("MangaDex HTTP $code: ${body.take(120)}")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
