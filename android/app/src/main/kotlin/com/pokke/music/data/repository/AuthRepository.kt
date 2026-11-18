package com.pokke.music.data.repository

import android.content.Context
import com.pokke.music.data.model.Playlist
import com.pokke.music.data.model.Song
import com.pokke.music.data.model.UserProfile
import com.pokke.music.data.remote.KokoroConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Kokoro account, profile, favorites and playlist repository. */
class AuthRepository(context: Context) {
    private companion object {
        const val POLIC_API_BASE = "https://polic.onrender.com"
    }
    data class AuthSession(
        val accessToken: String,
        val refreshToken: String?,
        val userId: String,
        val email: String?,
        val expiresAt: Long
    )

    private val store = SecureSessionStore(context)
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session

    suspend fun restoreSession(): Result<AuthSession?> = withContext(Dispatchers.IO) {
        runCatching {
            var saved = store.read()
            if (saved != null && saved.expiresAt <= System.currentTimeMillis() / 1000 + 90) {
                val refreshed = refreshSessionInternal(saved)
                if (refreshed != null) saved = refreshed
                else if (saved.expiresAt <= System.currentTimeMillis() / 1000) {
                    store.clear()
                    saved = null
                }
            }
            _session.value = saved
            saved
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            require(email.isNotBlank() && password.isNotBlank()) { "Completa el correo y la contraseña" }
            val json = authRequest(KokoroConfig.AUTH_TOKEN, JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            })
            val session = parseSession(json)
            store.save(session)
            _session.value = session
            ensureProfile(session, email.substringBefore('@'))
            session
        }
    }

    /** Returns null when email confirmation is required and Supabase did not issue a session. */
    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthSession?> = withContext(Dispatchers.IO) {
        runCatching {
            require(email.isNotBlank() && password.length >= 6) { "Usa un correo válido y una contraseña de al menos 6 caracteres" }
            val json = authRequest(KokoroConfig.AUTH_SIGNUP, JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().put("display_name", displayName.trim().ifBlank { email.substringBefore('@') }))
            })
            val token = json.optString("access_token")
            if (token.isBlank()) return@runCatching null
            val session = parseSession(json)
            store.save(session)
            _session.value = session
            ensureProfile(session, displayName.ifBlank { email.substringBefore('@') })
            session
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentSession()
            if (current != null) runCatching { request(KokoroConfig.AUTH_LOGOUT, "POST", current, body = "{}") }
            store.clear()
            _session.value = null
        }
    }

    /** Sends a lightweight request to wake the independent POLIC Render service. */
    suspend fun wakePolicServer(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$POLIC_API_BASE/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                // Any HTTP response means the Render web process answered; auth is not needed here.
                connection.responseCode
                Unit
            } finally { connection.disconnect() }
        }
    }

    /** Starts the official Discord OAuth2 flow through the Pokke backend. */
    suspend fun startDiscordOAuth(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val response = policRequest(
                "/api/auth/discord?userId=${encode(s.userId)}",
                "GET", s.accessToken
            )
            val json = JSONObject(response)
            json.optString("authorizationUrl").takeIf { it.startsWith("https://discord.com/oauth2/authorize") }
                ?: throw IOException("El servidor no devolvió una URL OAuth2 válida")
        }
    }

    /** Reads the currently stored profile-card theme from POLIC. */
    suspend fun getPolicTheme(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            JSONObject(policRequest("/api/users/${encode(s.userId)}/theme", "GET", s.accessToken))
        }
    }

    /** Applies the theme returned by POLIC to the signed-in Pokke profile. */
    suspend fun applyPolicTheme(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val remote = JSONObject(policRequest("/api/users/${encode(s.userId)}/theme", "GET", s.accessToken))
            val body = JSONObject().apply {
                remote.optString("backgroundColor").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.let { put("primary_color", it) }
                remote.optString("secondaryColor").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.let { put("secondary_color", it) }
                remote.optString("textColor").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.let { put("name_color", it) }
                remote.optString("accentColor").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.let { put("accent_color", it) }
                remote.optString("cardStyle").takeIf { it in setOf("glass", "solid", "minimal", "gradient") }?.let { put("card_style", it) }
                if (remote.has("gradientEnabled")) put("gradient_enabled", remote.optBoolean("gradientEnabled"))
                remote.optString("nameplateStyle").takeIf { it in setOf("aurora", "neon", "royal", "minimal", "cyber") }?.let { put("nameplate_style", it) }
                remote.optString("nameFont").takeIf { it in setOf("sans", "serif", "mono", "cursive") }?.let { put("name_font", it) }
                remote.optString("bannerUrl").takeIf { it.startsWith("https://") }?.let { put("search_card_banner_url", it) }
                remote.optString("backgroundUrl").takeIf { it.startsWith("https://") }?.let { put("custom_bg_url", it) }
            }
            request(KokoroConfig.PROFILES, "PATCH", s, params = mapOf("id" to "eq.${s.userId}"), body = body.toString(), prefer = "return=minimal")
            Unit
        }
    }

    /** Pushes the current Pokke profile-card design to POLIC/Supabase. */
    suspend fun syncPolicTheme(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val profileRows = JSONArray(request(KokoroConfig.PROFILES, "GET", s, params = mapOf(
                "id" to "eq.${s.userId}", "select" to "*", "limit" to "1"
            )))
            if (profileRows.length() == 0) throw IOException("No se encontró el perfil de Pokke")
            val p = parseProfile(profileRows.getJSONObject(0))
            val theme = JSONObject().apply {
                put("themeId", p.theme ?: "default")
                put("backgroundColor", p.primaryColor ?: "#1e1e2e")
                put("secondaryColor", p.secondaryColor ?: "#313244")
                put("textColor", p.nameColor ?: "#cdd6f4")
                put("accentColor", p.accentColor ?: "#5865F2")
                put("cardStyle", p.cardStyle ?: "glass")
                put("gradientEnabled", p.gradientEnabled)
                put("nameplateStyle", p.nameplateStyle ?: "aurora")
                put("nameFont", p.nameFont ?: "sans")
                put("bannerUrl", p.searchCardBannerUrl ?: p.bannerUrl ?: JSONObject.NULL)
                put("backgroundUrl", p.customBgUrl ?: JSONObject.NULL)
                put("updatedAt", java.time.Instant.now().toString())
            }
            policRequest("/api/users/${encode(s.userId)}/theme", "PUT", s.accessToken, theme.toString())
            Unit
        }
    }

    private fun policRequest(path: String, method: String, accessToken: String, body: String? = null): String {
        val connection = (URL("$POLIC_API_BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull().orEmpty()
                throw IOException("POLIC respondió HTTP $status${message.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
            }
            return response
        } finally { connection.disconnect() }
    }

    suspend fun getProfile(): Result<UserProfile?> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val rows = JSONArray(request(KokoroConfig.PROFILES, "GET", s, params = mapOf("id" to "eq.${s.userId}", "select" to "*", "limit" to "1")))
            if (rows.length() == 0) null else parseProfile(rows.getJSONObject(0))
        }
    }


    /** Upload bytes to public storage bucket and return public URL. */
    suspend fun uploadPublicImage(folder: String, fileName: String, bytes: ByteArray, mime: String = "image/jpeg"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val s = requireSession()
                val path = "$folder/${s.userId}/$fileName"
                val url = URL("${KokoroConfig.SUPABASE_URL}/storage/v1/object/avatars/$path")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${s.accessToken}")
                    setRequestProperty("Content-Type", mime)
                    setRequestProperty("x-upsert", "true")
                }
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText().orEmpty()
                conn.disconnect()
                if (code !in 200..299) throw IOException("Upload falló HTTP $code: $resp")
                "${KokoroConfig.SUPABASE_URL}/storage/v1/object/public/avatars/$path"
            }
        }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
        theme: String? = null,
        frame: String? = null,
        badge: String? = null,
        pronouns: String? = null,
        statusText: String? = null,
        accentColor: String? = null,
        cardStyle: String? = null,
        badges: String? = null,
        isPublic: Boolean? = null,
        linkLabel: String? = null,
        linkUrl: String? = null,
        showListening: Boolean? = null,
        extraLinks: String? = null,
        favoriteMangas: String? = null,
        primaryColor: String? = null,
        secondaryColor: String? = null,
        gradientEnabled: Boolean? = null,
        profileGifs: String? = null,
        searchCardStyle: String? = null,
        searchCardColor: String? = null,
        searchCardEffect: String? = null,
        searchCardBannerUrl: String? = null,
        customBgUrl: String? = null,
        aboutExtra: String? = null,
        nameplateStyle: String? = null,
        nameFont: String? = null,
        nameColor: String? = null,
        profileCardOrder: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            require(username.matches(Regex("[a-zA-Z0-9_.]{3,24}"))) { "El usuario debe tener 3–24 caracteres: letras, números, _ o ." }
            val body = JSONObject().apply {
                put("id", s.userId)
                put("display_name", displayName.trim())
                put("username", username.trim().lowercase())
                put("bio", bio.trim())
                if (avatarUrl != null) { if (avatarUrl.isBlank()) put("avatar_url", JSONObject.NULL) else put("avatar_url", avatarUrl.trim()) }
                if (bannerUrl != null) { if (bannerUrl.isBlank()) put("banner_url", JSONObject.NULL) else put("banner_url", bannerUrl.trim()) }
                if (primaryColor != null) put("primary_color", primaryColor)
                if (secondaryColor != null) put("secondary_color", secondaryColor)
                if (gradientEnabled != null) put("gradient_enabled", gradientEnabled)
                if (profileGifs != null) put("profile_gifs", profileGifs)
                if (searchCardStyle != null) put("search_card_style", searchCardStyle)
                if (searchCardColor != null) put("search_card_color", searchCardColor)
                if (searchCardEffect != null) put("search_card_effect", searchCardEffect)
                if (aboutExtra != null) put("about_extra", aboutExtra)
                if (nameplateStyle != null) put("nameplate_style", nameplateStyle.takeIf { it in setOf("aurora", "neon", "royal", "minimal", "cyber") } ?: "aurora")
                if (nameFont != null) put("name_font", nameFont.takeIf { it in setOf("sans", "serif", "mono", "cursive") } ?: "sans")
                if (nameColor != null) put("name_color", nameColor.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#F3F2FA")
                if (profileCardOrder != null) put("profile_card_order", profileCardOrder.take(80))
                if (searchCardBannerUrl != null) {
                    if (searchCardBannerUrl.isBlank()) put("search_card_banner_url", JSONObject.NULL)
                    else put("search_card_banner_url", searchCardBannerUrl.trim())
                }
                if (customBgUrl != null) {
                    if (customBgUrl.isBlank()) put("custom_bg_url", JSONObject.NULL)
                    else put("custom_bg_url", customBgUrl.trim())
                }
                if (theme != null) put("theme", theme)
                if (frame != null) put("frame", frame)
                if (badge != null) put("badge", badge)
                if (pronouns != null) put("pronouns", pronouns)
                if (statusText != null) put("status_text", statusText)
                if (accentColor != null) put("accent_color", accentColor)
                if (cardStyle != null) put("card_style", cardStyle)
                if (badges != null) put("badges", badges)
                if (isPublic != null) put("is_public", isPublic)
                if (linkLabel != null) put("link_label", linkLabel)
                if (linkUrl != null) put("link_url", linkUrl)
                if (showListening != null) put("show_listening", showListening)
                if (extraLinks != null) put("extra_links", extraLinks)
                if (favoriteMangas != null) put("favorite_mangas", favoriteMangas)
            }
            val rows = JSONArray(request(KokoroConfig.PROFILES, "POST", s, params = mapOf("on_conflict" to "id"), body = body.toString(), prefer = "resolution=merge-duplicates,return=representation"))
            if (rows.length() == 0) throw IOException("Pokke Users no devolvió el perfil actualizado")
            val updatedProfile = parseProfile(rows.getJSONObject(0))
            // Theme sync is best-effort: POLIC downtime must never block saving the Pokke profile.
            runCatching {
                val theme = JSONObject().apply {
                    put("themeId", updatedProfile.theme ?: "default")
                    put("backgroundColor", updatedProfile.primaryColor ?: "#1e1e2e")
                    put("secondaryColor", updatedProfile.secondaryColor ?: "#313244")
                    put("textColor", updatedProfile.nameColor ?: "#cdd6f4")
                    put("accentColor", updatedProfile.accentColor ?: "#5865F2")
                    put("cardStyle", updatedProfile.cardStyle ?: "glass")
                    put("gradientEnabled", updatedProfile.gradientEnabled)
                    put("nameplateStyle", updatedProfile.nameplateStyle ?: "aurora")
                    put("nameFont", updatedProfile.nameFont ?: "sans")
                    put("bannerUrl", updatedProfile.searchCardBannerUrl ?: updatedProfile.bannerUrl ?: JSONObject.NULL)
                    put("backgroundUrl", updatedProfile.customBgUrl ?: JSONObject.NULL)
                    put("updatedAt", java.time.Instant.now().toString())
                }
                policRequest("/api/users/${encode(s.userId)}/theme", "PUT", s.accessToken, theme.toString())
            }
            updatedProfile
        }
    }


    data class FollowStats(val followers: Int, val following: Int, val isFollowing: Boolean)

    /** Read follow counts and whether the signed-in user follows this profile. */
    suspend fun getFollowStats(profileId: String): Result<FollowStats> = withContext(Dispatchers.IO) {
        runCatching {
            val me = session.value
            val followers = JSONArray(http(
                "/rest/v1/pokke_user_follows", "GET",
                params = mapOf("following_id" to "eq.$profileId", "select" to "follower_id", "limit" to "1000"),
                authToken = me?.accessToken
            )).length()
            val following = JSONArray(http(
                "/rest/v1/pokke_user_follows", "GET",
                params = mapOf("follower_id" to "eq.$profileId", "select" to "following_id", "limit" to "1000"),
                authToken = me?.accessToken
            )).length()
            val isFollowing = if (me == null || me.userId == profileId) false else {
                JSONArray(request("/rest/v1/pokke_user_follows", "GET", me, params = mapOf(
                    "follower_id" to "eq.${me.userId}", "following_id" to "eq.$profileId", "select" to "follower_id", "limit" to "1"
                ))).length() > 0
            }
            FollowStats(followers, following, isFollowing)
        }
    }

    suspend fun setFollowing(profileId: String, follow: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val me = requireSession()
            require(me.userId != profileId) { "No puedes seguirte a ti mismo" }
            if (follow) {
                val payload = JSONArray().put(JSONObject().put("follower_id", me.userId).put("following_id", profileId))
                request("/rest/v1/pokke_user_follows", "POST", me, body = payload.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
            } else {
                request("/rest/v1/pokke_user_follows", "DELETE", me, params = mapOf(
                    "follower_id" to "eq.${me.userId}", "following_id" to "eq.$profileId"
                ), prefer = "return=minimal")
            }
            Unit
        }
    }

    /** Public lookup by username (only returns is_public = true profiles unless it's your own). */
    
    data class ListeningPresence(
        val userId: String,
        val songId: String?,
        val title: String?,
        val artist: String?,
        val coverUrl: String?,
        val audioUrl: String?,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean
    )

    suspend fun publishListening(
        songId: String?,
        title: String?,
        artist: String?,
        coverUrl: String?,
        audioUrl: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = session.value ?: return@runCatching
            val obj = JSONObject()
                .put("user_id", s.userId)
                .put("song_id", songId ?: JSONObject.NULL)
                .put("title", title ?: JSONObject.NULL)
                .put("artist", artist ?: JSONObject.NULL)
                .put("cover_url", coverUrl ?: JSONObject.NULL)
                .put("audio_url", audioUrl ?: JSONObject.NULL)
                .put("position_ms", positionMs)
                .put("duration_ms", durationMs)
                .put("is_playing", isPlaying)
                .put("updated_at", java.time.Instant.now().toString())
            request(
                "/rest/v1/pokke_listening_presence",
                "POST",
                s,
                body = JSONArray().put(obj).toString(),
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            Unit
        }
    }

    suspend fun getListeningForUser(userId: String): Result<ListeningPresence?> = withContext(Dispatchers.IO) {
        runCatching {
            val s = session.value
            val body = if (s != null) {
                request(
                    "/rest/v1/pokke_listening_presence",
                    "GET",
                    s,
                    params = mapOf(
                        "user_id" to "eq.$userId",
                        "select" to "*",
                        "order" to "updated_at.desc",
                        "limit" to "1"
                    )
                )
            } else {
                http(
                    "/rest/v1/pokke_listening_presence",
                    "GET",
                    params = mapOf(
                        "user_id" to "eq.$userId",
                        "select" to "*",
                        "order" to "updated_at.desc",
                        "limit" to "1"
                    )
                )
            }
            val arr = JSONArray(body)
            if (arr.length() == 0) null
            else {
                val o = arr.getJSONObject(0)
                ListeningPresence(
                    userId = o.optString("user_id"),
                    songId = o.optString("song_id").takeIf { it.isNotBlank() && it != "null" },
                    title = o.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    artist = o.optString("artist").takeIf { it.isNotBlank() && it != "null" },
                    coverUrl = o.optString("cover_url").takeIf { it.isNotBlank() && it != "null" },
                    audioUrl = o.optString("audio_url").takeIf { it.isNotBlank() && it != "null" },
                    positionMs = o.optLong("position_ms"),
                    durationMs = o.optLong("duration_ms"),
                    isPlaying = o.optBoolean("is_playing", true)
                ).takeIf { it.isPlaying && !it.title.isNullOrBlank() }
            }
        }
    }

    suspend fun getPublicFavorites(username: String): Result<List<com.pokke.music.data.model.Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = session.value
            val body = if (s != null) {
                request(
                    "/rest/v1/rpc/pokke_public_favorites",
                    "POST",
                    s,
                    body = JSONObject().put("p_username", username).toString()
                )
            } else {
                http(
                    "/rest/v1/rpc/pokke_public_favorites",
                    "POST",
                    body = JSONObject().put("p_username", username).toString()
                )
            }
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.pokke.music.data.model.Song(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    coverUrl = o.optString("cover_url").takeIf { it.isNotBlank() && it != "null" },
                    audioUrl = o.optString("audio_url").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }


    suspend fun getProfileByUsername(username: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = username.trim().lowercase().removePrefix("@")
            require(clean.isNotBlank()) { "Escribe un nombre de usuario" }
            val s = _session.value
            val params = mutableMapOf(
                "username" to "eq.$clean",
                "select" to "*",
                "limit" to "1"
            )
            val body = if (s != null) {
                request(KokoroConfig.PROFILES, "GET", s, params = params)
            } else {
                // Anonymous read (requires RLS allowing public profiles)
                http(KokoroConfig.PROFILES, "GET", params)
            }
            val rows = JSONArray(body)
            if (rows.length() == 0) null else {
                val p = parseProfile(rows.getJSONObject(0))
                if (!p.isPublic && p.id != s?.userId) null else p
            }
        }
    }

    /** Search public profiles by username or display name. */
    suspend fun searchUsers(query: String, limit: Int = 30): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Escribe algo para buscar" }
            val term = query.trim().lowercase().removePrefix("@")
            val pattern = "*$term*"
            val s = _session.value
            val params = mapOf(
                "select" to "id,display_name,username,avatar_url,banner_url,custom_bg_url,bio,theme,frame,is_public,badge,pronouns,status_text,accent_color,card_style,badges,link_label,link_url,show_listening,extra_links,favorite_mangas,search_card_style,search_card_color,search_card_effect,primary_color,secondary_color,gradient_enabled,profile_gifs,about_extra,is_owner,nameplate_id,nameplate_image_url,nameplate_primary_color,nameplate_secondary_color,nameplate_style,name_font,name_color,profile_card_order",
                "or" to "(username.ilike.$pattern,display_name.ilike.$pattern)",
                "is_public" to "eq.true",
                "order" to "username.asc",
                "limit" to limit.coerceIn(1, 50).toString()
            )
            val body = if (s != null) request(KokoroConfig.PROFILES, "GET", s, params = params)
            else http(KokoroConfig.PROFILES, "GET", params)
            val rows = JSONArray(body)
            (0 until rows.length()).map { parseProfile(rows.getJSONObject(it)) }
        }
    }

    suspend fun getFavorites(): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val rows = JSONArray(request(KokoroConfig.FAVORITES, "GET", s, params = mapOf(
                "user_id" to "eq.${s.userId}", "select" to "song_id,title,artist,cover_url,duration,stream_url", "order" to "created_at.desc"
            )))
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                Song(
                    id = row.optString("song_id"), title = row.optString("title"), artist = row.optString("artist"),
                    coverUrl = row.optString("cover_url").nullable(), audioUrl = row.optString("stream_url").nullable(),
                    durationSeconds = row.optInt("duration", 0)
                )
            }
        }
    }

    suspend fun isFavorite(songId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val rows = JSONArray(request(KokoroConfig.FAVORITES, "GET", s, params = mapOf(
                "user_id" to "eq.${s.userId}", "song_id" to "eq.$songId", "select" to "id", "limit" to "1"
            )))
            rows.length() > 0
        }
    }

    suspend fun setFavorite(song: Song, favorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            if (favorite) {
                val body = JSONObject().apply {
                    put("user_id", s.userId); put("song_id", song.id); put("title", song.title); put("artist", song.artist)
                    put("cover_url", song.displayCoverUrl); put("duration", song.durationSeconds); put("stream_url", song.audioUrl)
                }
                request(KokoroConfig.FAVORITES, "POST", s, params = mapOf("on_conflict" to "user_id,song_id"), body = body.toString(), prefer = "resolution=merge-duplicates,return=minimal")
            } else {
                request(KokoroConfig.FAVORITES, "DELETE", s, params = mapOf("user_id" to "eq.${s.userId}", "song_id" to "eq.${song.id}"))
            }
            Unit
        }
    }

    /** Download account-wide Tori appearance/Home preferences from Kokoro. */
    suspend fun getAccountAppSettings(): Result<JSONObject?> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val rows = JSONArray(request(KokoroConfig.TORI_USER_APP_STATE, "GET", s, params = mapOf(
                "user_id" to "eq.${s.userId}", "select" to "settings", "limit" to "1"
            )))
            if (rows.length() == 0) null else rows.getJSONObject(0).optJSONObject("settings")
        }
    }

    /** Upsert account-wide Tori appearance/Home preferences under the signed-in user. */
    suspend fun saveAccountAppSettings(settings: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val payload = JSONArray().put(JSONObject().apply {
                put("user_id", s.userId)
                put("settings", settings)
                put("updated_at", java.time.Instant.now().toString())
            })
            request(
                KokoroConfig.TORI_USER_APP_STATE,
                "POST",
                s,
                params = mapOf("on_conflict" to "user_id"),
                body = payload.toString(),
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            Unit
        }
    }

    suspend fun getPlaylists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val playlistParams = mapOf(
                "user_id" to "eq.${s.userId}",
                "select" to "id,name,description,cover_url,is_public,created_at,primary_color,secondary_color,visual_style",
                "order" to "updated_at.desc"
            )
            val rows = runCatching { JSONArray(request(KokoroConfig.PLAYLISTS, "GET", s, params = playlistParams)) }
                .getOrElse {
                    // Older Supabase schemas remain readable until the visual-fields migration is applied.
                    JSONArray(request(KokoroConfig.PLAYLISTS, "GET", s, params = mapOf(
                        "user_id" to "eq.${s.userId}",
                        "select" to "id,name,description,cover_url,is_public,created_at",
                        "order" to "updated_at.desc"
                    )))
                }
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val id = row.getString("id")
                val countRows = JSONArray(request(KokoroConfig.PLAYLIST_SONGS, "GET", s, params = mapOf("playlist_id" to "eq.$id", "select" to "id")))
                Playlist(
                    id, row.optString("name"), row.optString("cover_url").nullable(), row.optString("description").nullable(),
                    row.optBoolean("is_public"), countRows.length(), row.optString("primary_color", "#6750A4"),
                    row.optString("secondary_color", "#26324A"), row.optString("visual_style", "gradient")
                )
            }
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            require(name.isNotBlank()) { "Escribe un nombre para la playlist" }
            val rows = JSONArray(request(KokoroConfig.PLAYLISTS, "POST", s, body = JSONObject().apply {
                put("user_id", s.userId); put("name", name.trim()); put("description", description.trim()); put("is_public", false)
            }.toString(), prefer = "return=representation"))
            if (rows.length() == 0) throw IOException("No se pudo crear la playlist")
            val row = rows.getJSONObject(0)
            Playlist(
                row.getString("id"), row.getString("name"), row.optString("cover_url").nullable(),
                row.optString("description").nullable(), row.optBoolean("is_public"), 0,
                row.optString("primary_color", "#6750A4"), row.optString("secondary_color", "#26324A"),
                row.optString("visual_style", "gradient")
            )
        }
    }

    suspend fun updatePlaylistDetails(
        playlistId: String,
        name: String,
        description: String,
        coverUrl: String?,
        isPublic: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            require(name.isNotBlank()) { "Escribe un nombre para la playlist" }
            require(name.length <= 80) { "El nombre no puede superar 80 caracteres" }
            require(description.length <= 500) { "La descripción no puede superar 500 caracteres" }
            request(
                KokoroConfig.PLAYLISTS,
                "PATCH",
                s,
                params = mapOf("id" to "eq.$playlistId", "user_id" to "eq.${s.userId}"),
                body = JSONObject().put("name", name.trim())
                    .put("description", description.trim())
                    .put("cover_url", coverUrl ?: JSONObject.NULL)
                    .put("is_public", isPublic)
                    .put("updated_at", java.time.Instant.now().toString()).toString(),
                prefer = "return=minimal"
            )
            Unit
        }
    }

    suspend fun updatePlaylistAppearance(
        playlistId: String,
        primaryColor: String,
        secondaryColor: String,
        visualStyle: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            require(primaryColor.matches(Regex("#[0-9A-Fa-f]{6}")))
            require(secondaryColor.matches(Regex("#[0-9A-Fa-f]{6}")))
            require(visualStyle in setOf("gradient", "solid", "glass"))
            request(
                KokoroConfig.PLAYLISTS,
                "PATCH",
                s,
                params = mapOf("id" to "eq.$playlistId", "user_id" to "eq.${s.userId}"),
                body = JSONObject().put("primary_color", primaryColor).put("secondary_color", secondaryColor)
                    .put("visual_style", visualStyle).put("updated_at", java.time.Instant.now().toString()).toString(),
                prefer = "return=minimal"
            )
            Unit
        }
    }

    /** Public share links may resolve only playlists explicitly marked public. */
    suspend fun getPublicPlaylistById(playlistId: String): Result<Pair<Playlist, List<Song>>?> = withContext(Dispatchers.IO) {
        runCatching {
            require(playlistId.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "Identificador de playlist no válido" }
            val playlistParams = mapOf(
                "id" to "eq.$playlistId", "is_public" to "eq.true",
                "select" to "id,name,description,cover_url,is_public,primary_color,secondary_color,visual_style",
                "limit" to "1"
            )
            val activeSession = _session.value
            val playlistBody = if (activeSession != null) request(KokoroConfig.PLAYLISTS, "GET", activeSession, params = playlistParams)
                else http(KokoroConfig.PLAYLISTS, "GET", params = playlistParams)
            val rows = JSONArray(playlistBody)
            if (rows.length() == 0) return@runCatching null
            val row = rows.getJSONObject(0)
            val songsParams = mapOf(
                "playlist_id" to "eq.$playlistId",
                "select" to "song_id,title,artist,cover_url,duration,stream_url",
                "order" to "position.asc,added_at.asc"
            )
            val songsBody = if (activeSession != null) request(KokoroConfig.PLAYLIST_SONGS, "GET", activeSession, params = songsParams)
                else http(KokoroConfig.PLAYLIST_SONGS, "GET", params = songsParams)
            val songsRows = JSONArray(songsBody)
            val songs = (0 until songsRows.length()).map { index ->
                val item = songsRows.getJSONObject(index)
                Song(
                    id = item.optString("song_id"), title = item.optString("title", "Sin título"),
                    artist = item.optString("artist", "Artista desconocido"),
                    coverUrl = item.optString("cover_url").nullable(),
                    audioUrl = item.optString("stream_url").nullable(),
                    durationSeconds = item.optInt("duration")
                )
            }
            val playlist = Playlist(
                id = row.optString("id"), name = row.optString("name"),
                coverUrl = row.optString("cover_url").nullable(), description = row.optString("description").nullable(),
                isPublic = true, songCount = songs.size,
                primaryColor = row.optString("primary_color", "#6750A4"),
                secondaryColor = row.optString("secondary_color", "#26324A"),
                visualStyle = row.optString("visual_style", "gradient")
            )
            playlist to songs
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val rows = JSONArray(request(KokoroConfig.PLAYLIST_SONGS, "GET", s, params = mapOf(
                "playlist_id" to "eq.$playlistId", "select" to "song_id,title,artist,cover_url,duration,stream_url", "order" to "position.asc,added_at.asc"
            )))
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                Song(row.optString("song_id"), row.optString("title"), row.optString("artist"), coverUrl = row.optString("cover_url").nullable(), audioUrl = row.optString("stream_url").nullable(), durationSeconds = row.optInt("duration"))
            }
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val existing = JSONArray(request(KokoroConfig.PLAYLIST_SONGS, "GET", s, params = mapOf(
                "playlist_id" to "eq.$playlistId", "song_id" to "eq.${song.id}", "select" to "id", "limit" to "1"
            )))
            if (existing.length() == 0) {
                val body = JSONObject().apply {
                    put("playlist_id", playlistId); put("song_id", song.id); put("title", song.title); put("artist", song.artist)
                    put("cover_url", song.displayCoverUrl); put("duration", song.durationSeconds); put("stream_url", song.audioUrl)
                }
                request(KokoroConfig.PLAYLIST_SONGS, "POST", s, body = body.toString(), prefer = "return=minimal")
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            request(KokoroConfig.PLAYLIST_SONGS, "DELETE", s, params = mapOf("playlist_id" to "eq.$playlistId", "song_id" to "eq.$songId"))
            Unit
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            request(KokoroConfig.PLAYLIST_SONGS, "DELETE", s, params = mapOf("playlist_id" to "eq.$playlistId"))
            request(KokoroConfig.PLAYLISTS, "DELETE", s, params = mapOf("id" to "eq.$playlistId"))
            Unit
        }
    }

    private suspend fun currentSession(): AuthSession? {
        var s = _session.value ?: store.read()?.also { _session.value = it } ?: return null
        if (s.expiresAt <= System.currentTimeMillis() / 1000 + 90) {
            val refreshed = refreshSessionInternal(s)
            if (refreshed != null) {
                s = refreshed
                _session.value = s
            } else if (s.expiresAt <= System.currentTimeMillis() / 1000) {
                store.clear()
                _session.value = null
                return null
            }
        }
        return s
    }

    private suspend fun requireSession(): AuthSession = currentSession() ?: throw IOException("Inicia sesión para sincronizar tus datos")

    private fun refreshSessionInternal(old: AuthSession): AuthSession? {
        val refresh = old.refreshToken ?: return null
        return runCatching {
            val json = authRequest(KokoroConfig.AUTH_REFRESH, JSONObject().put("refresh_token", refresh))
            parseSession(json).also { store.save(it) }
        }.getOrNull()
    }

    private fun ensureProfile(session: AuthSession, suggestedName: String) {
        runCatching {
            val existing = JSONArray(request(KokoroConfig.PROFILES, "GET", session, params = mapOf("id" to "eq.${session.userId}", "select" to "id", "limit" to "1")))
            if (existing.length() == 0) {
                val body = JSONObject().put("id", session.userId).put("display_name", suggestedName)
                request(KokoroConfig.PROFILES, "POST", session, body = body.toString(), prefer = "return=minimal")
            }
        }
    }

    private fun parseSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: throw IOException("Supabase no devolvió el usuario")
        val access = json.optString("access_token")
        if (access.isBlank()) throw IOException("Supabase no devolvió el token de acceso")
        val expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
        return AuthSession(access, json.optString("refresh_token").nullable(), user.getString("id"), user.optString("email").nullable(), expiresAt)
    }


    /** Owner: grant emblem by username or user uuid. */
    suspend fun grantEmblem(usernameOrId: String, emblemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireSession()
            val target = usernameOrId.trim().removePrefix("@")
            require(target.isNotBlank() && emblemId.isNotBlank()) { "Usuario y emblema requeridos" }
            val userId = if (target.matches(Regex("[0-9a-fA-F-]{36}"))) target else {
                val rows = JSONArray(request(KokoroConfig.PROFILES, "GET", s, params = mapOf(
                    "username" to "eq.${target.lowercase()}", "select" to "id", "limit" to "1"
                )))
                if (rows.length() == 0) throw IOException("Usuario no encontrado")
                rows.getJSONObject(0).getString("id")
            }
            val body = JSONObject().put("user_id", userId).put("emblem_id", emblemId).put("granted_by", s.userId)
            request("/rest/v1/user_emblems", "POST", s, params = mapOf("on_conflict" to "user_id,emblem_id"), body = body.toString(), prefer = "resolution=merge-duplicates,return=minimal")
            Unit
        }
    }

        private fun parseProfile(o: JSONObject) = UserProfile(
        id = o.getString("id"),
        displayName = o.optString("display_name").nullable(),
        username = o.optString("username").nullable(),
        avatarUrl = o.optString("avatar_url").nullable(),
        bannerUrl = o.optString("banner_url").nullable(),
        bio = o.optString("bio").nullable(),
        theme = o.optString("theme", "default"),
        frame = o.optString("frame", "none"),
        isPublic = o.optBoolean("is_public", true),
        isArtist = o.optBoolean("is_artist", false),
        badge = o.optString("badge", "none"),
        pronouns = o.optString("pronouns").nullable(),
        statusText = o.optString("status_text").nullable(),
        accentColor = o.optString("accent_color", "violet"),
        cardStyle = o.optString("card_style", "glass"),
        badges = o.optString("badges").nullable(),
        linkLabel = o.optString("link_label").nullable(),
        linkUrl = o.optString("link_url").nullable(),
        showListening = if (o.has("show_listening")) o.optBoolean("show_listening", true) else true,
        extraLinks = o.optString("extra_links").nullable(),
        favoriteMangas = o.optString("favorite_mangas").nullable(),
        searchCardStyle = o.optString("search_card_style").nullable(),
        searchCardColor = o.optString("search_card_color").nullable(),
        searchCardEffect = o.optString("search_card_effect").nullable(),
        primaryColor = o.optString("primary_color").nullable(),
        secondaryColor = o.optString("secondary_color").nullable(),
        gradientEnabled = if (o.has("gradient_enabled")) o.optBoolean("gradient_enabled", true) else true,
        profileGifs = o.optString("profile_gifs").nullable(),
        aboutExtra = o.optString("about_extra").nullable(),
        isOwner = o.optBoolean("is_owner", false),
        searchCardBannerUrl = o.optString("search_card_banner_url").nullable(),
        customBgUrl = o.optString("custom_bg_url").nullable(),
        nameplateId = o.optString("nameplate_id").nullable(),
        nameplateImageUrl = o.optString("nameplate_image_url").nullable(),
        nameplatePrimaryColor = o.optString("nameplate_primary_color").nullable(),
        nameplateSecondaryColor = o.optString("nameplate_secondary_color").nullable(),
        nameplateStyle = o.optString("nameplate_style", "aurora"),
        nameFont = o.optString("name_font", "sans"),
        nameColor = o.optString("name_color", "#F3F2FA"),
        profileCardOrder = o.optString("profile_card_order", "listening,badges,mangas")
    )

    private fun authRequest(path: String, body: JSONObject): JSONObject = JSONObject(http(path, "POST", body = body.toString(), authToken = null))

    private fun request(
        path: String, method: String, session: AuthSession, params: Map<String, String> = emptyMap(),
        body: String? = null, prefer: String? = null
    ): String = http(path, method, params, body, session.accessToken, prefer)

    private fun http(
        path: String, method: String, params: Map<String, String> = emptyMap(), body: String? = null,
        authToken: String? = null, prefer: String? = null
    ): String {
        val query = if (params.isEmpty()) "" else params.entries.joinToString("&", prefix = "?") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = (URL("${KokoroConfig.SUPABASE_URL}$path$query").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
            if (authToken != null) setRequestProperty("Authorization", "Bearer $authToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val e = JSONObject(response)
                    e.optString("msg").ifBlank { e.optString("message") }.ifBlank { e.optString("error_description") }.ifBlank { e.optString("error") }
                }.getOrNull().orEmpty()
                throw IOException("Pokke Users respondió HTTP $status${detail.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""}")
            }
            return response
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun String?.nullable() = this?.takeIf { it.isNotBlank() && it != "null" }
}
