package com.pokke.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class DeezerArtist(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val fans: Long?
)

/** Public Deezer artist search. No API key or user credentials are required. */
class DeezerArtistRepository {
    suspend fun searchArtists(query: String): Result<List<DeezerArtist>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim().take(80)
            require(clean.length >= 2) { "Escribe al menos dos caracteres" }
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val connection = (URL("https://api.deezer.com/search/artist?q=$encoded&limit=30").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Pokke/1.0 Android")
            }
            try {
                if (connection.responseCode !in 200..299) error("Deezer respondió HTTP ${connection.responseCode}")
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val data = root.optJSONArray("data") ?: org.json.JSONArray()
                buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optLong("id", -1L)
                        val name = item.optString("name").trim()
                        if (id <= 0 || name.isBlank()) continue
                        val image = item.optString("picture_big").takeIf { it.isNotBlank() && it != "null" }
                            ?: item.optString("picture_medium").takeIf { it.isNotBlank() && it != "null" }
                        add(DeezerArtist(id, name, image, item.optLong("nb_fan", -1L).takeIf { it >= 0 }))
                    }
                }
            } finally { connection.disconnect() }
        }
    }
}
