package com.pokke.music.data.remote

/**
 * Giphy keys: primary + backup when quota is exhausted.
 * Do not commit real keys to public repos if the project is open-source;
 * prefer BuildConfig / local.properties in production.
 */
object GiphyConfig {
    const val PRIMARY_KEY = "y1UYimEs46Hujw3Tnod2dj8zYO8fkKoq"
    const val BACKUP_KEY = "PH4rbBOCP2JAb5MLrEz3ErgnUy1ZKlDd"
    @Volatile private var useBackup = false

    fun apiKey(): String = if (useBackup) BACKUP_KEY else PRIMARY_KEY
    fun markPrimaryExhausted() { useBackup = true }
    fun searchUrl(query: String, limit: Int = 24): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return "https://api.giphy.com/v1/gifs/search?api_key=${apiKey()}&q=$q&limit=$limit&rating=pg-13"
    }
}
