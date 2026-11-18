package com.pokke.music.auth.external

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ExternalSession(
    val provider: String, // spotify | ytmusic
    val label: String,
    val cookie: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Sesiones externas cifradas de Spotify / YouTube Music.
 * Discord usa OAuth2 oficial del backend y nunca se guarda aquí.
 */
class ExternalAuthStore(context: Context) {
    private val prefs: SharedPreferences =
        EncryptedPrefs.open(context, "pokke_ext_auth", "pokke_ext_auth_plain")

    fun sessions(): List<ExternalSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ExternalSession(
                            provider = o.getString("provider"),
                            label = o.optString("label"),
                            cookie = o.getString("cookie"),
                            addedAt = o.optLong("addedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(session: ExternalSession) {
        val list = sessions().filterNot { it.provider == session.provider && it.label == session.label } + session
        save(list)
    }

    fun remove(provider: String, label: String) {
        save(sessions().filterNot { it.provider == provider && it.label == label })
    }

    fun clearProvider(provider: String) {
        save(sessions().filterNot { it.provider == provider })
    }

    private fun save(list: List<ExternalSession>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("provider", it.provider)
                    .put("label", it.label)
                    .put("cookie", it.cookie)
                    .put("addedAt", it.addedAt)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "sessions_json"
        private val API_SID_NAMES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

        /** True only when the cookie header contains a usable Innertube signing secret. */
        fun hasApiSid(cookieHeader: String): Boolean = cookieHeader.split(';').any { entry ->
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            name in API_SID_NAMES && value.isNotEmpty()
        }
    }
}
