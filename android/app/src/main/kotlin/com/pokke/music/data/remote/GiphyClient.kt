package com.pokke.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GiphyGif(val id: String, val title: String, val previewUrl: String, val fullUrl: String)

object GiphyClient {
    // Primary + backup keys provided by owner
    private val keys = listOf(
        "y1UYimEs46Hujw3Tnod2dj8zYO8fkKoq",
        "PH4rbBOCP2JAb5MLrEz3ErgnUy1ZKlDd"
    )
    private var keyIndex = 0

    suspend fun search(query: String, limit: Int = 24): List<GiphyGif> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        var last: Exception? = null
        for (i in keys.indices) {
            val key = keys[(keyIndex + i) % keys.size]
            try {
                val q = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://api.giphy.com/v1/gifs/search?api_key=$key&q=$q&limit=$limit&rating=pg-13")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
                if (code == 429 || code == 403) {
                    keyIndex = (keyIndex + 1) % keys.size
                    last = Exception("Giphy $code")
                    continue
                }
                if (code !in 200..299) throw Exception("Giphy HTTP $code")
                val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
                val out = mutableListOf<GiphyGif>()
                for (j in 0 until arr.length()) {
                    val o = arr.getJSONObject(j)
                    val images = o.getJSONObject("images")
                    val preview = images.optJSONObject("fixed_width_small")?.optString("url")
                        ?: images.optJSONObject("preview_gif")?.optString("url")
                        ?: continue
                    val full = images.optJSONObject("original")?.optString("url")
                        ?: images.optJSONObject("fixed_width")?.optString("url")
                        ?: preview
                    out.add(GiphyGif(o.optString("id"), o.optString("title"), preview, full))
                }
                return@withContext out
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: Exception("Giphy error")
    }

    suspend fun trending(limit: Int = 24): List<GiphyGif> = withContext(Dispatchers.IO) {
        search("trending", limit).ifEmpty {
            // fallback endpoint
            val key = keys[keyIndex]
            val url = URL("https://api.giphy.com/v1/gifs/trending?api_key=$key&limit=$limit&rating=pg-13")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { j ->
                val o = arr.getJSONObject(j)
                val images = o.getJSONObject("images")
                val preview = images.optJSONObject("fixed_width_small")?.optString("url") ?: return@mapNotNull null
                val full = images.optJSONObject("original")?.optString("url") ?: preview
                GiphyGif(o.optString("id"), o.optString("title"), preview, full)
            }
        }
    }
}
