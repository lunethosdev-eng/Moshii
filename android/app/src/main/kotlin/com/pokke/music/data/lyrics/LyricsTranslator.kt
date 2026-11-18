package com.pokke.music.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Multi-backend lyric translation. Tries several free endpoints so a single
 * rate-limit does not kill the feature.
 *
 * Order: Lingva mirrors → MyMemory → LibreTranslate.
 */
object LyricsTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val lingvaHosts = listOf(
        "https://lingva.ml",
        "https://lingva.garudalinux.org",
        "https://translate.plausibility.cloud",
    )

    suspend fun translate(text: String, targetLang: String, sourceLang: String = "auto"): String? =
        withContext(Dispatchers.IO) {
            val q = text.trim().take(4500)
            if (q.isBlank()) return@withContext null
            val target = targetLang.ifBlank { "es" }

            translateLingva(q, sourceLang, target)
                ?: translateMyMemory(q, sourceLang, target)
                ?: translateLibre(q, sourceLang, target)
        }

    suspend fun translateLines(lines: List<String>, targetLang: String): List<String> {
        if (lines.isEmpty()) return emptyList()
        // Batch in chunks to stay under URL/body limits
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val chunk = lines.subList(i, minOf(i + 12, lines.size))
            val joined = chunk.joinToString("\n")
            val translated = translate(joined, targetLang) ?: joined
            val parts = translated.split("\n")
            for (j in chunk.indices) {
                out += parts.getOrNull(j)?.takeIf { it.isNotBlank() } ?: chunk[j]
            }
            i += chunk.size
        }
        return out
    }

    private fun translateLingva(q: String, source: String, target: String): String? {
        val src = if (source == "auto") "auto" else source
        for (host in lingvaHosts) {
            runCatching {
                val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
                val url = "$host/api/v1/$src/$target/$encoded"
                val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val tr = JSONObject(body).optString("translation")
                    tr.takeIf { it.isNotBlank() }
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun translateMyMemory(q: String, source: String, target: String): String? = runCatching {
        val langpair = if (source == "auto") "autodetect|$target" else "$source|$target"
        val url = "https://api.mymemory.translated.net/get?q=" +
            URLEncoder.encode(q.take(500), "UTF-8") +
            "&langpair=" + URLEncoder.encode(langpair, "UTF-8")
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string().orEmpty()
            val tr = JSONObject(body).optJSONObject("responseData")?.optString("translatedText")
            tr?.takeIf { it.isNotBlank() && !it.contains("MYMEMORY WARNING", true) }
        }
    }.getOrNull()

    private fun translateLibre(q: String, source: String, target: String): String? {
        val hosts = listOf(
            "https://libretranslate.com",
            "https://translate.argosopentech.com",
        )
        for (host in hosts) {
            runCatching {
                val body = FormBody.Builder()
                    .add("q", q)
                    .add("source", if (source == "auto") "auto" else source)
                    .add("target", target)
                    .add("format", "text")
                    .build()
                val req = Request.Builder().url("$host/translate").post(body)
                    .header("Accept", "application/json").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    JSONObject(resp.body?.string().orEmpty()).optString("translatedText")
                        .takeIf { it.isNotBlank() }
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }
}
