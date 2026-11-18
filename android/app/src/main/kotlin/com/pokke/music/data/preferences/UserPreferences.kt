package com.pokke.music.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.pokke.music.ui.motion.MotionSettings
import org.json.JSONObject
import org.json.JSONArray

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pokke_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        private val EXTERNAL_ACCOUNTS = booleanPreferencesKey("external_accounts_enabled")
        private val SPOTIFY_LOGIN = booleanPreferencesKey("spotify_login_enabled")
        private val DISCORD_LOGIN = booleanPreferencesKey("discord_login_enabled")
        private val DISCORD_POLIC_LINKED = booleanPreferencesKey("discord_polic_linked")
        private val YTMUSIC_LOGIN = booleanPreferencesKey("ytmusic_login_enabled")
        private val VENDORED_GLASS = booleanPreferencesKey("vendored_backdrop_glass")

        private val LIQUID_GLASS = booleanPreferencesKey("liquid_glass_enabled")
        private val LIQUID_SLIDER = booleanPreferencesKey("liquid_slider_enabled")
        private val LIQUID_PERFORMANCE = booleanPreferencesKey("liquid_glass_performance_mode")
        private val EXPERIMENTAL_CHATS = booleanPreferencesKey("experimental_chats_alpha_1")
        private val EXPERIMENTAL_MAPS = booleanPreferencesKey("experimental_friend_maps_alpha_1")
        private val AUTO_ANIMATED_COVERS = booleanPreferencesKey("auto_animated_covers")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        private val BLUR_PLAYER = booleanPreferencesKey("blur_player_background")
        private val GAPLESS = booleanPreferencesKey("gapless_playback")
        private val CROSSFADE = floatPreferencesKey("crossfade_seconds")
        private val ACCENT = stringPreferencesKey("accent_color")
        private val THEME = stringPreferencesKey("app_theme")
        private val SHOW_LISTENING = booleanPreferencesKey("show_listening_on_profile")
        private val SHOW_LINK_EMBEDS = booleanPreferencesKey("show_link_embeds")
        private val PLAYER_THEME = stringPreferencesKey("player_theme") // minimal | sakura | midnight | aurora

        // YouTube / external catalog (separate from login)
        private val YOUTUBE_CATALOG = booleanPreferencesKey("youtube_catalog_enabled")
        private val LYRICS_TARGET_LANG = stringPreferencesKey("lyrics_target_lang")
        private val LYRICS_COLOR_MODE = stringPreferencesKey("lyrics_color_mode")
        private val LYRICS_ANIM_INTENSITY = floatPreferencesKey("lyrics_anim_intensity")
        private val HOME_CUSTOM_BG = stringPreferencesKey("home_custom_bg_url")
        private val APP_ACCENT = stringPreferencesKey("app_accent_theme")
        private val SPOTIFY_CANVAS = booleanPreferencesKey("spotify_canvas_enabled")
        private val APPLE_CANVAS = booleanPreferencesKey("apple_canvas_enabled")
        private val POKKE_ANIMATED_COVERS = booleanPreferencesKey("pokke_animated_covers")
        private val EXTERNAL_CANVAS = booleanPreferencesKey("external_canvas_enabled")
        private val CANVAS_REPEAT = booleanPreferencesKey("canvas_repeat_enabled")
        private val PLAYER_LAST_SCREEN = stringPreferencesKey("player_last_screen_v1")

        // Home personalization
        private val HOME_SHOW_RECENT = booleanPreferencesKey("home_show_recent")
        private val HOME_SHOW_NOW_PLAYING = booleanPreferencesKey("home_show_now_playing")
        private val HOME_SHOW_QUICK_ACCESS = booleanPreferencesKey("home_show_quick_access")
        private val HOME_SHOW_RECOMMENDATIONS = booleanPreferencesKey("home_show_recommendations")
        private val HOME_SHOW_LIBRARY_SHORTCUTS = booleanPreferencesKey("home_show_library_shortcuts")
        private val HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        private val HOME_LAYOUT = stringPreferencesKey("home_layout") // compact | comfortable | dense
        private val HOME_DENSITY = stringPreferencesKey("home_density") // small | medium | large

        // Tori Motion Engine
        private val MOTION_ENABLED = booleanPreferencesKey("tori_motion_enabled")
        private val MOTION_PROFILE = stringPreferencesKey("tori_motion_profile")
        private val MOTION_INTENSITY = floatPreferencesKey("tori_motion_intensity")
        private val MOTION_SPEED = floatPreferencesKey("tori_motion_speed")
        private val MOTION_DEPTH = floatPreferencesKey("tori_motion_depth")
        private val MOTION_REFLECTIONS = booleanPreferencesKey("tori_motion_reflections")
        private val MOTION_PARALLAX = booleanPreferencesKey("tori_motion_parallax")
        private val MOTION_HAPTICS = booleanPreferencesKey("tori_motion_haptics")
        private val MOTION_REDUCE = booleanPreferencesKey("tori_motion_reduce")
        private val SEARCH_HISTORY = stringPreferencesKey("search_history_v1")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed_v1")
        private val ONBOARDING_DRAFT = stringPreferencesKey("onboarding_draft_v1")
        private val ONBOARDING_NICKNAME = stringPreferencesKey("onboarding_nickname_v1")
        private val ONBOARDING_ARTISTS = stringPreferencesKey("onboarding_artists_v1")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { values ->
        // Existing installs already have persisted preferences; don't force an upgrade onboarding on them.
        values[ONBOARDING_COMPLETED] ?: values.asMap().isNotEmpty()
    }
    val onboardingDraft: Flow<String> = context.dataStore.data.map { it[ONBOARDING_DRAFT] ?: "{}" }
    val onboardingNickname: Flow<String> = context.dataStore.data.map { it[ONBOARDING_NICKNAME] ?: "" }
    val onboardingArtists: Flow<String> = context.dataStore.data.map { it[ONBOARDING_ARTISTS] ?: "[]" }

    suspend fun saveOnboardingDraft(raw: String) { context.dataStore.edit { it[ONBOARDING_DRAFT] = raw.take(16000) } }
    suspend fun completeOnboarding(nickname: String, artistsJson: String) {
        context.dataStore.edit {
            it[ONBOARDING_NICKNAME] = nickname.trim().take(32)
            it[ONBOARDING_ARTISTS] = artistsJson.take(12000)
            it[ONBOARDING_COMPLETED] = true
            it[ONBOARDING_DRAFT] = "{}"
        }
    }
    suspend fun reopenOnboarding() {
        context.dataStore.edit { values ->
            val draft = JSONObject().apply {
                put("step", 0)
                put("nickname", values[ONBOARDING_NICKNAME] ?: "")
                put("providerIndex", 0)
                put("providers", JSONArray().apply {
                    if (values[SPOTIFY_LOGIN] == true) put("spotify")
                    if (values[YTMUSIC_LOGIN] == true) put("ytmusic")
                    if (values[DISCORD_LOGIN] == true) put("discord")
                })
                put("artists", runCatching { JSONArray(values[ONBOARDING_ARTISTS] ?: "[]") }.getOrDefault(JSONArray()))
            }
            values[ONBOARDING_DRAFT] = draft.toString()
            values[ONBOARDING_COMPLETED] = false
        }
    }

    val externalAccountsEnabled: Flow<Boolean> = context.dataStore.data.map { it[EXTERNAL_ACCOUNTS] ?: false }
    val spotifyLoginEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPOTIFY_LOGIN] ?: false }
    val discordLoginEnabled: Flow<Boolean> = context.dataStore.data.map { it[DISCORD_LOGIN] ?: false }
    val discordPolicLinked: Flow<Boolean> = context.dataStore.data.map { it[DISCORD_POLIC_LINKED] ?: false }
    val ytMusicLoginEnabled: Flow<Boolean> = context.dataStore.data.map { it[YTMUSIC_LOGIN] ?: false }
    val vendoredBackdropGlass: Flow<Boolean> = context.dataStore.data.map { it[VENDORED_GLASS] ?: true }

    suspend fun setExternalAccountsEnabled(v: Boolean) { context.dataStore.edit { it[EXTERNAL_ACCOUNTS] = v } }
    suspend fun setSpotifyLoginEnabled(v: Boolean) { context.dataStore.edit { it[SPOTIFY_LOGIN] = v } }
    suspend fun setDiscordLoginEnabled(v: Boolean) { context.dataStore.edit { it[DISCORD_LOGIN] = v } }
    suspend fun setDiscordPolicLinked(v: Boolean) { context.dataStore.edit { it[DISCORD_POLIC_LINKED] = v } }
    suspend fun setYtMusicLoginEnabled(v: Boolean) { context.dataStore.edit { it[YTMUSIC_LOGIN] = v } }
    suspend fun setVendoredBackdropGlass(v: Boolean) { context.dataStore.edit { it[VENDORED_GLASS] = v } }

    val youtubeCatalogEnabled: Flow<Boolean> = context.dataStore.data.map { it[YOUTUBE_CATALOG] ?: false }
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        runCatching { org.json.JSONArray(prefs[SEARCH_HISTORY] ?: "[]").let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }.distinct().take(12) } }.getOrDefault(emptyList())
    }
    val lyricsTargetLang: Flow<String> = context.dataStore.data.map { it[LYRICS_TARGET_LANG] ?: "es" }
    val lyricsColorMode: Flow<String> = context.dataStore.data.map { it[LYRICS_COLOR_MODE] ?: "classic" }
    val lyricsAnimIntensity: Flow<Float> = context.dataStore.data.map { it[LYRICS_ANIM_INTENSITY] ?: 1f }
    val homeCustomBgUrl: Flow<String> = context.dataStore.data.map { it[HOME_CUSTOM_BG] ?: "" }
    val appAccentTheme: Flow<String> = context.dataStore.data.map { it[APP_ACCENT] ?: "violet" }
    val spotifyCanvasEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPOTIFY_CANVAS] ?: false }
    val appleCanvasEnabled: Flow<Boolean> = context.dataStore.data.map { it[APPLE_CANVAS] ?: false }
    val pokkeAnimatedCoversEnabled: Flow<Boolean> = context.dataStore.data.map { it[POKKE_ANIMATED_COVERS] ?: true }
    val externalCanvasEnabled: Flow<Boolean> = context.dataStore.data.map { it[EXTERNAL_CANVAS] ?: true }
    val canvasRepeatEnabled: Flow<Boolean> = context.dataStore.data.map { it[CANVAS_REPEAT] ?: true }
    val playerLastScreen: Flow<String> = context.dataStore.data.map {
        it[PLAYER_LAST_SCREEN]?.uppercase()?.takeIf { value -> value in setOf("MAIN", "LYRICS", "QUEUE") } ?: "MAIN"
    }

    suspend fun setYoutubeCatalogEnabled(v: Boolean) { context.dataStore.edit { it[YOUTUBE_CATALOG] = v } }
    suspend fun addSearchHistory(query: String) {
        val clean = query.trim().take(100)
        if (clean.length < 2) return
        context.dataStore.edit { prefs ->
            val old = runCatching { org.json.JSONArray(prefs[SEARCH_HISTORY] ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList())
            prefs[SEARCH_HISTORY] = org.json.JSONArray((listOf(clean) + old.filterNot { it.equals(clean, true) }).take(12)).toString()
        }
    }
    suspend fun removeSearchHistory(query: String) {
        context.dataStore.edit { prefs ->
            val old = runCatching { org.json.JSONArray(prefs[SEARCH_HISTORY] ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList())
            prefs[SEARCH_HISTORY] = org.json.JSONArray(old.filterNot { it.equals(query, true) }).toString()
        }
    }
    suspend fun clearSearchHistory() { context.dataStore.edit { it[SEARCH_HISTORY] = "[]" } }
    suspend fun setLyricsTargetLang(v: String) { context.dataStore.edit { it[LYRICS_TARGET_LANG] = v } }
    suspend fun setLyricsColorMode(v: String) { context.dataStore.edit { it[LYRICS_COLOR_MODE] = v } }
    suspend fun setLyricsAnimIntensity(v: Float) { context.dataStore.edit { it[LYRICS_ANIM_INTENSITY] = v.coerceIn(0.3f, 1.8f) } }
    suspend fun setHomeCustomBgUrl(v: String) { context.dataStore.edit { it[HOME_CUSTOM_BG] = v } }
    suspend fun setAppAccentTheme(v: String) { context.dataStore.edit { it[APP_ACCENT] = v } }
    suspend fun setSpotifyCanvasEnabled(v: Boolean) { context.dataStore.edit { it[SPOTIFY_CANVAS] = v } }
    suspend fun setAppleCanvasEnabled(v: Boolean) { context.dataStore.edit { it[APPLE_CANVAS] = v } }
    suspend fun setPokkeAnimatedCoversEnabled(v: Boolean) { context.dataStore.edit { it[POKKE_ANIMATED_COVERS] = v } }
    suspend fun setExternalCanvasEnabled(v: Boolean) { context.dataStore.edit { it[EXTERNAL_CANVAS] = v } }
    suspend fun setCanvasRepeatEnabled(v: Boolean) { context.dataStore.edit { it[CANVAS_REPEAT] = v } }
    suspend fun setPlayerLastScreen(v: String) {
        val safe = v.uppercase().takeIf { it in setOf("MAIN", "LYRICS", "QUEUE") } ?: "MAIN"
        context.dataStore.edit { it[PLAYER_LAST_SCREEN] = safe }
    }

    val liquidGlassEnabled: Flow<Boolean> = context.dataStore.data.map { it[LIQUID_GLASS] ?: false }
    val liquidSliderEnabled: Flow<Boolean> = context.dataStore.data.map { it[LIQUID_SLIDER] ?: true }
    val liquidPerformanceMode: Flow<Boolean> = context.dataStore.data.map { it[LIQUID_PERFORMANCE] ?: true }
    val experimentalChatsEnabled: Flow<Boolean> = context.dataStore.data.map { it[EXPERIMENTAL_CHATS] ?: false }
    val experimentalMapsEnabled: Flow<Boolean> = context.dataStore.data.map { it[EXPERIMENTAL_MAPS] ?: false }
    val autoAnimatedCovers: Flow<Boolean> = context.dataStore.data.map { it[AUTO_ANIMATED_COVERS] ?: true }
    val dynamicColors: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLORS] ?: true }
    val blurPlayerBackground: Flow<Boolean> = context.dataStore.data.map { it[BLUR_PLAYER] ?: false }
    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[GAPLESS] ?: true }
    val crossfadeSeconds: Flow<Float> = context.dataStore.data.map { it[CROSSFADE] ?: 0f }
    val accentColor: Flow<String> = context.dataStore.data.map { it[ACCENT] ?: "white" }
    val appTheme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "dark" }
    /** Default ON: others can see what you are listening to on your public profile. */
    val showListeningOnProfile: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LISTENING] ?: true }
    /** Default ON: link cards expand / show rich preview when possible. */
    val showLinkEmbeds: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LINK_EMBEDS] ?: true }
    val playerTheme: Flow<String> = context.dataStore.data.map { it[PLAYER_THEME] ?: "minimal" }

    // Home personalization flows
    val homeShowRecent: Flow<Boolean> = context.dataStore.data.map { it[HOME_SHOW_RECENT] ?: true }
    val homeShowNowPlaying: Flow<Boolean> = context.dataStore.data.map { it[HOME_SHOW_NOW_PLAYING] ?: true }
    val homeShowQuickAccess: Flow<Boolean> = context.dataStore.data.map { it[HOME_SHOW_QUICK_ACCESS] ?: true }
    val homeShowRecommendations: Flow<Boolean> = context.dataStore.data.map { it[HOME_SHOW_RECOMMENDATIONS] ?: true }
    val homeShowLibraryShortcuts: Flow<Boolean> = context.dataStore.data.map { it[HOME_SHOW_LIBRARY_SHORTCUTS] ?: true }
    val homeSectionOrder: Flow<String> = context.dataStore.data.map {
        it[HOME_SECTION_ORDER] ?: "now_playing,quick_access,recent,recommendations,library"
    }
    val homeLayout: Flow<String> = context.dataStore.data.map { it[HOME_LAYOUT] ?: "comfortable" }
    val homeDensity: Flow<String> = context.dataStore.data.map { it[HOME_DENSITY] ?: "medium" }

    /** Account-level appearance and Home layout. Player presentation is deliberately excluded. */
    val accountSyncedSettings: Flow<String> = context.dataStore.data.map { values ->
        JSONObject().apply {
            put("version", 1)
            put("theme", values[THEME] ?: "dark")
            put("accentColor", values[ACCENT] ?: "white")
            put("accentTheme", values[APP_ACCENT] ?: "violet")
            put("dynamicColors", values[DYNAMIC_COLORS] ?: true)
            put("liquidGlass", values[LIQUID_GLASS] ?: false)
            put("homeBackground", values[HOME_CUSTOM_BG] ?: "")
            put("homeShowRecent", values[HOME_SHOW_RECENT] ?: true)
            put("homeShowNowPlaying", values[HOME_SHOW_NOW_PLAYING] ?: true)
            put("homeShowQuickAccess", values[HOME_SHOW_QUICK_ACCESS] ?: true)
            put("homeShowRecommendations", values[HOME_SHOW_RECOMMENDATIONS] ?: true)
            put("homeShowLibraryShortcuts", values[HOME_SHOW_LIBRARY_SHORTCUTS] ?: true)
            put("homeSectionOrder", values[HOME_SECTION_ORDER] ?: "now_playing,quick_access,recent,recommendations,library")
            put("homeLayout", values[HOME_LAYOUT] ?: "comfortable")
            put("homeDensity", values[HOME_DENSITY] ?: "medium")
        }.toString()
    }

    /** Apply a validated server snapshot without syncing player-specific preferences. */
    suspend fun applyAccountSyncedSettings(raw: String) {
        val json = JSONObject(raw)
        context.dataStore.edit { values ->
            values[THEME] = json.optString("theme", values[THEME] ?: "dark").takeIf { it in setOf("dark", "light", "system") } ?: "dark"
            values[ACCENT] = json.optString("accentColor", values[ACCENT] ?: "white").take(32)
            values[APP_ACCENT] = json.optString("accentTheme", values[APP_ACCENT] ?: "violet").take(32)
            values[DYNAMIC_COLORS] = json.optBoolean("dynamicColors", values[DYNAMIC_COLORS] ?: true)
            values[LIQUID_GLASS] = json.optBoolean("liquidGlass", values[LIQUID_GLASS] ?: false)
            values[HOME_CUSTOM_BG] = json.optString("homeBackground", values[HOME_CUSTOM_BG] ?: "").take(2048)
            values[HOME_SHOW_RECENT] = json.optBoolean("homeShowRecent", values[HOME_SHOW_RECENT] ?: true)
            values[HOME_SHOW_NOW_PLAYING] = json.optBoolean("homeShowNowPlaying", values[HOME_SHOW_NOW_PLAYING] ?: true)
            values[HOME_SHOW_QUICK_ACCESS] = json.optBoolean("homeShowQuickAccess", values[HOME_SHOW_QUICK_ACCESS] ?: true)
            values[HOME_SHOW_RECOMMENDATIONS] = json.optBoolean("homeShowRecommendations", values[HOME_SHOW_RECOMMENDATIONS] ?: true)
            values[HOME_SHOW_LIBRARY_SHORTCUTS] = json.optBoolean("homeShowLibraryShortcuts", values[HOME_SHOW_LIBRARY_SHORTCUTS] ?: true)
            values[HOME_SECTION_ORDER] = json.optString("homeSectionOrder", values[HOME_SECTION_ORDER] ?: "now_playing,quick_access,recent,recommendations,library").take(256)
            values[HOME_LAYOUT] = json.optString("homeLayout", values[HOME_LAYOUT] ?: "comfortable").takeIf { it in setOf("compact", "comfortable", "dense") } ?: "comfortable"
            values[HOME_DENSITY] = json.optString("homeDensity", values[HOME_DENSITY] ?: "medium").takeIf { it in setOf("small", "medium", "large") } ?: "medium"
        }
    }

    suspend fun setLiquidGlass(v: Boolean) = context.dataStore.edit { it[LIQUID_GLASS] = v }
    suspend fun setLiquidSlider(v: Boolean) = context.dataStore.edit { it[LIQUID_SLIDER] = v }
    suspend fun setLiquidPerformanceMode(v: Boolean) = context.dataStore.edit { it[LIQUID_PERFORMANCE] = v }
    suspend fun setExperimentalChats(v: Boolean) = context.dataStore.edit { it[EXPERIMENTAL_CHATS] = v; if (!v) it[EXPERIMENTAL_MAPS] = false }
    suspend fun setExperimentalMaps(v: Boolean) = context.dataStore.edit { it[EXPERIMENTAL_MAPS] = v }
    suspend fun setAutoAnimatedCovers(v: Boolean) = context.dataStore.edit { it[AUTO_ANIMATED_COVERS] = v }
    suspend fun setDynamicColors(v: Boolean) = context.dataStore.edit { it[DYNAMIC_COLORS] = v }
    suspend fun setBlurPlayerBackground(v: Boolean) = context.dataStore.edit { it[BLUR_PLAYER] = v }
    suspend fun setGaplessPlayback(v: Boolean) = context.dataStore.edit { it[GAPLESS] = v }
    suspend fun setCrossfadeSeconds(v: Float) = context.dataStore.edit { it[CROSSFADE] = v.coerceIn(0f, 12f) }
    suspend fun setAccentColor(v: String) = context.dataStore.edit { it[ACCENT] = v }
    suspend fun setAppTheme(v: String) = context.dataStore.edit { it[THEME] = v }
    suspend fun setShowListeningOnProfile(v: Boolean) = context.dataStore.edit { it[SHOW_LISTENING] = v }
    suspend fun setShowLinkEmbeds(v: Boolean) = context.dataStore.edit { it[SHOW_LINK_EMBEDS] = v }
    suspend fun setPlayerTheme(v: String) = context.dataStore.edit { it[PLAYER_THEME] = v }

    suspend fun setHomeShowRecent(v: Boolean) = context.dataStore.edit { it[HOME_SHOW_RECENT] = v }
    suspend fun setHomeShowNowPlaying(v: Boolean) = context.dataStore.edit { it[HOME_SHOW_NOW_PLAYING] = v }
    suspend fun setHomeShowQuickAccess(v: Boolean) = context.dataStore.edit { it[HOME_SHOW_QUICK_ACCESS] = v }
    suspend fun setHomeShowRecommendations(v: Boolean) = context.dataStore.edit { it[HOME_SHOW_RECOMMENDATIONS] = v }
    suspend fun setHomeShowLibraryShortcuts(v: Boolean) = context.dataStore.edit { it[HOME_SHOW_LIBRARY_SHORTCUTS] = v }
    suspend fun setHomeSectionOrder(v: String) = context.dataStore.edit { it[HOME_SECTION_ORDER] = v }
    suspend fun setHomeLayout(v: String) = context.dataStore.edit { it[HOME_LAYOUT] = v }
    suspend fun setHomeDensity(v: String) = context.dataStore.edit { it[HOME_DENSITY] = v }


    val motionSettings: Flow<MotionSettings> = context.dataStore.data.map { prefs ->
        MotionSettings(
            enabled = prefs[MOTION_ENABLED] ?: true,
            profile = prefs[MOTION_PROFILE] ?: MotionSettings.PROFILE_BALANCED,
            intensity = prefs[MOTION_INTENSITY] ?: 1f,
            speed = prefs[MOTION_SPEED] ?: 1f,
            depth = prefs[MOTION_DEPTH] ?: 0.65f,
            reflections = prefs[MOTION_REFLECTIONS] ?: true,
            parallax = prefs[MOTION_PARALLAX] ?: true,
            haptics = prefs[MOTION_HAPTICS] ?: true,
            reduceMotion = prefs[MOTION_REDUCE] ?: false,
        )
    }

    suspend fun setMotionEnabled(v: Boolean) { context.dataStore.edit { it[MOTION_ENABLED] = v } }
    suspend fun setMotionProfile(v: String) {
        val safe = v.takeIf { it in setOf(MotionSettings.PROFILE_FULL, MotionSettings.PROFILE_BALANCED, MotionSettings.PROFILE_LIGHT) }
            ?: MotionSettings.PROFILE_BALANCED
        context.dataStore.edit { it[MOTION_PROFILE] = safe }
    }
    suspend fun setMotionIntensity(v: Float) { context.dataStore.edit { it[MOTION_INTENSITY] = v.coerceIn(0f, 1.8f) } }
    suspend fun setMotionSpeed(v: Float) { context.dataStore.edit { it[MOTION_SPEED] = v.coerceIn(0.5f, 1.8f) } }
    suspend fun setMotionDepth(v: Float) { context.dataStore.edit { it[MOTION_DEPTH] = v.coerceIn(0f, 1f) } }
    suspend fun setMotionReflections(v: Boolean) { context.dataStore.edit { it[MOTION_REFLECTIONS] = v } }
    suspend fun setMotionParallax(v: Boolean) { context.dataStore.edit { it[MOTION_PARALLAX] = v } }
    suspend fun setMotionHaptics(v: Boolean) { context.dataStore.edit { it[MOTION_HAPTICS] = v } }
    suspend fun setMotionReduce(v: Boolean) { context.dataStore.edit { it[MOTION_REDUCE] = v } }

    /** Restore default Home layout and visibility. */
    suspend fun resetHomeCustomization() = context.dataStore.edit {
        it[HOME_SHOW_RECENT] = true
        it[HOME_SHOW_NOW_PLAYING] = true
        it[HOME_SHOW_QUICK_ACCESS] = true
        it[HOME_SHOW_RECOMMENDATIONS] = true
        it[HOME_SHOW_LIBRARY_SHORTCUTS] = true
        it[HOME_SECTION_ORDER] = "now_playing,quick_access,recent,recommendations,library"
        it[HOME_LAYOUT] = "comfortable"
        it[HOME_DENSITY] = "medium"
    }
}
