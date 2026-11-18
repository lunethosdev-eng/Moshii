package com.pokke.music.data.repository

import com.pokke.music.data.remote.KokoroConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class UserEmblemRepository {
    suspend fun hasDeveloperEmblem(session: AuthRepository.AuthSession): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uid = URLEncoder.encode(session.userId, "UTF-8")
            val ownedUrl = URL("${KokoroConfig.SUPABASE_URL}/rest/v1/user_emblems?user_id=eq.$uid&select=emblem_id")
            val owned = JSONArray(request(ownedUrl, session))
            val hasDeveloper = (0 until owned.length()).any { owned.optJSONObject(it)?.optString("emblem_id") == "developer" }
            if (!hasDeveloper) return@runCatching false

            // Cross-check against the server-managed catalog; the client never grants emblems.
            val catalogUrl = URL("${KokoroConfig.SUPABASE_URL}/rest/v1/emblem_catalog?id=eq.developer&select=id,label,icon,color")
            val catalog = JSONArray(request(catalogUrl, session))
            (0 until catalog.length()).any { catalog.optJSONObject(it)?.optString("id") == "developer" }
        }.getOrDefault(false)
    }

    private fun request(url: URL, session: AuthRepository.AuthSession): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IOException("No se pudieron consultar los emblemas (HTTP $code).")
        return body
    }
}
