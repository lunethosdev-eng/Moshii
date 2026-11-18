package com.pokke.music.data.canvas

import android.util.Log
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.normalizeForMatch
import com.music.bitchord.data.canvas.splitArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/** Spotify Canvas connector ported from the supplied BitChord source bundle. */
object SpotifyCanvasProvider {
    private const val TAG = "PokkeSpotifyCanvas"
    private const val SEARCH = "https://api.spotify.com/v1/search"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(22, TimeUnit.SECONDS).build()
    private val hashes = SpotifyCanvasQuery.QueryHashes(fetch = { url -> get(url) }, maxScripts = 3)

    suspend fun find(title: String, artist: String, album: String?): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext null
        val bearer = runCatching { SpotifyToken.accessToken() }.getOrNull() ?: return@withContext null
        val query = listOf(title, artist, album).filterNotNull().filter(String::isNotBlank).joinToString(" ")
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "8")
            .build().toString()
        val headers = mutableMapOf("Authorization" to "Bearer $bearer", "Accept" to "application/json", "User-Agent" to CANVAS_UA)
        SpotifyToken.clientToken()?.let { headers["Client-Token"] = it }
        val body = get(url, headers) ?: return@withContext null
        val items = runCatching { json.parseToJsonElement(body).jsonObject["tracks"]!!.jsonObject["items"]!!.jsonArray }.getOrNull()
            ?: return@withContext null
        val wantedTitle = title.normalizeForMatch()
        val wantedArtists = splitArtists(artist)
        val hit = items.firstNotNullOfOrNull { element ->
            val track = runCatching { element.jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
            val name = track["name"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
            val artists = runCatching { track["artists"]!!.jsonArray.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull } }.getOrDefault(emptyList())
            val artistParts = artists.flatMap(::splitArtists)
            val titleOk = name.normalizeForMatch() == wantedTitle
            val artistOk = wantedArtists.isNotEmpty() && wantedArtists.all { want -> artistParts.any { it == want } }
            if (!titleOk || !artistOk) return@firstNotNullOfOrNull null
            val uri = track["uri"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
            val albumName = runCatching { track["album"]!!.jsonObject["name"]!!.jsonPrimitive.contentOrNull }.getOrNull()
            Triple(uri, name, albumName)
        } ?: return@withContext null

        val hash = hashes.canvasHash()
        val requestBody = SpotifyCanvasQuery.requestBody(hit.first, hash).toRequestBody("application/json".toMediaType())
        val canvasRequest = Request.Builder().url(SpotifyCanvasQuery.ENDPOINT).post(requestBody)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", CANVAS_UA)
            .apply { SpotifyToken.clientToken()?.let { header("Client-Token", it) } }
            .build()
        val response = runCatching { http.newCall(canvasRequest).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
            ?: return@withContext null
        when (val answer = SpotifyCanvasQuery.parse(response)) {
            is SpotifyCanvasQuery.Answer.Found -> CanvasArtwork(url = answer.url, title = hit.second, artist = artist, album = hit.third, source = CanvasSource.SPOTIFY)
            is SpotifyCanvasQuery.Answer.NoCanvas -> { Log.d(TAG, "Spotify returned no playable Canvas: ${answer.detail}"); null }
            is SpotifyCanvasQuery.Answer.Failed -> { Log.w(TAG, "Spotify Canvas query failed: ${answer.reason}"); null }
        }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val request = Request.Builder().url(url).apply { headers.forEach { (key, value) -> header(key, value) } }.build()
        return runCatching { http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
    }

    private const val CANVAS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
}
