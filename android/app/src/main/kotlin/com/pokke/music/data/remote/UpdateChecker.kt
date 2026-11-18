package com.pokke.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val versionCodeHint: Int?
)

object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/lunethosdev-eng/okipokkeX/releases/latest"

    suspend fun fetchLatest(): Result<AppRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Pokke-Android")
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            try {
                if (conn.responseCode !in 200..299) error("GitHub HTTP ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val tag = json.optString("tag_name").removePrefix("v")
                val codeHint = tag.split(".").firstOrNull()?.toIntOrNull()
                    ?: Regex("(\\d+)").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
                AppRelease(
                    tag = tag,
                    name = json.optString("name").ifBlank { tag },
                    body = json.optString("body").orEmpty(),
                    htmlUrl = json.optString("html_url").orEmpty(),
                    versionCodeHint = codeHint
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    fun isNewer(latestTag: String, currentVersionName: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".", "-", "_")
            .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val a = parts(latestTag)
        val b = parts(currentVersionName)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
