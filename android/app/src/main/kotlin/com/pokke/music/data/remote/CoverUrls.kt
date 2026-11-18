package com.pokke.music.data.remote

object CoverUrls {
    fun absolute(raw: String?, sourceBaseUrl: String? = null): String? {
        val v = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        if (v.startsWith("http://", true) || v.startsWith("https://", true) ||
            v.startsWith("content://") || v.startsWith("file://")) return v
        val base = (sourceBaseUrl ?: UkiyoConfig.SUPABASE_URL).trimEnd('/')
        return when {
            v.startsWith("/storage/v1/") -> "$base$v"
            v.startsWith("storage/v1/") -> "$base/$v"
            v.startsWith("/object/") -> "$base/storage/v1$v"
            v.startsWith("object/") -> "$base/storage/v1/$v"
            '/' in v || v.contains('.') -> "$base/storage/v1/object/public/$v"
            else -> v
        }
    }
    fun fromYoutubeId(youtubeId: String?): String? =
        youtubeId?.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    fun best(coverUrl: String?, youtubeId: String?, sourceBaseUrl: String? = null): String? =
        absolute(coverUrl, sourceBaseUrl) ?: fromYoutubeId(youtubeId)
    fun fromDeezer(title: String?, artist: String?): String? {
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return null
        return runCatching {
            val q = java.net.URLEncoder.encode("$artist $title", "UTF-8")
            val c = (java.net.URL("https://api.deezer.com/search/track?q=$q&limit=1").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3500; readTimeout = 3500; requestMethod = "GET"
                setRequestProperty("User-Agent", "Pokke/1.0")
            }
            try {
                if (c.responseCode !in 200..299) return null
                val data = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return null
                if (data.length() == 0) return null
                val album = data.getJSONObject(0).optJSONObject("album") ?: return null
                album.optString("cover_xl").takeIf { it.isNotBlank() && it != "null" }
                    ?: album.optString("cover_big").takeIf { it.isNotBlank() && it != "null" }
                    ?: album.optString("cover_medium").takeIf { it.isNotBlank() && it != "null" }
            } finally { c.disconnect() }
        }.getOrNull()
    }
    fun fromItunes(title: String?, artist: String?): String? {
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return null
        return runCatching {
            val term = java.net.URLEncoder.encode("$artist $title", "UTF-8")
            val c = (java.net.URL("https://itunes.apple.com/search?term=$term&entity=song&limit=1").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3500; readTimeout = 3500; requestMethod = "GET"
                setRequestProperty("User-Agent", "Pokke/1.0")
            }
            try {
                if (c.responseCode !in 200..299) return null
                val results = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return null
                if (results.length() == 0) return null
                val artwork = results.getJSONObject(0).optString("artworkUrl100").takeIf { it.isNotBlank() && it != "null" } ?: return null
                artwork.replace("100x100bb", "600x600bb").replace("100x100", "600x600")
            } finally { c.disconnect() }
        }.getOrNull()
    }
    fun resolveBest(coverUrl: String?, youtubeId: String?, title: String?, artist: String?, sourceBaseUrl: String? = null): String? {
        best(coverUrl, youtubeId, sourceBaseUrl)?.let { return it }
        fromDeezer(title, artist)?.let { return it }
        return fromItunes(title, artist)
    }
}
