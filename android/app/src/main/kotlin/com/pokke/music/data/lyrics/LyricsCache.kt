package com.pokke.music.data.lyrics

import android.content.Context
import com.pokke.music.data.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Private, bounded on-device cache. Nothing is written to public/shared storage. */
object LyricsCache {
    private const val FILE_NAME = "pokke_lyrics_cache_v1.json"
    private const val MAX_BYTES = 8L * 1024L * 1024L
    private const val MAX_ENTRIES = 240
    private val maxAgeMs = TimeUnit.DAYS.toMillis(30)
    private val mutex = Mutex()

    data class CachedLyrics(val lines: List<LyricLine>, val provider: String?)

    suspend fun get(context: Context, key: String): CachedLyrics? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = file(context)
            if (!file.exists()) null else runCatching {
                val root = JSONObject(file.readText())
                val entries = root.optJSONArray("entries") ?: JSONArray()
                val entry = (0 until entries.length())
                    .mapNotNull { entries.optJSONObject(it) }
                    .firstOrNull { it.optString("key") == digest(key) }
                if (entry == null || System.currentTimeMillis() - entry.optLong("savedAt") > maxAgeMs) {
                    null
                } else {
                    val raw = entry.optJSONArray("lines") ?: JSONArray()
                    val lines = buildList {
                        for (i in 0 until raw.length()) {
                            val item = raw.optJSONObject(i) ?: continue
                            add(LyricLine(item.optLong("time"), item.optString("text")))
                        }
                    }
                    if (lines.isEmpty()) null
                    else CachedLyrics(lines, entry.optString("provider").takeIf { it.isNotBlank() })
                }
            }.getOrNull()
        }
    }

    suspend fun put(context: Context, key: String, lines: List<LyricLine>, provider: String? = null) = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext
        mutex.withLock {
            runCatching {
                val file = file(context)
                val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
                val old = root.optJSONArray("entries") ?: JSONArray()
                val entries = ArrayList<JSONObject>()
                val hash = digest(key)
                for (i in 0 until old.length()) {
                    val item = old.optJSONObject(i) ?: continue
                    if (item.optString("key") != hash && System.currentTimeMillis() - item.optLong("savedAt") <= maxAgeMs) entries += item
                }
                val lineArray = JSONArray().apply {
                    lines.forEach { put(JSONObject().put("time", it.timeMs).put("text", it.text)) }
                }
                entries += JSONObject()
                    .put("key", hash)
                    .put("savedAt", System.currentTimeMillis())
                    .put("provider", provider.orEmpty())
                    .put("lines", lineArray)
                val trimmed = entries.takeLast(MAX_ENTRIES)
                val output = JSONObject().put("entries", JSONArray(trimmed)).toString()
                file.parentFile?.mkdirs()
                file.writeText(output)
                if (file.length() > MAX_BYTES) {
                    // Keep the newest half if the bounded cache ever reaches its byte ceiling.
                    val keep = trimmed.takeLast(MAX_ENTRIES / 2)
                    file.writeText(JSONObject().put("entries", JSONArray(keep)).toString())
                }
            }
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { file(context).delete() } }
    }

    private fun file(context: Context) = File(context.filesDir, "lyrics-cache/$FILE_NAME")
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
