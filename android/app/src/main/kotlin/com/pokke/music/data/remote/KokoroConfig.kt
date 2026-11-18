package com.pokke.music.data.remote

/**
 * User accounts / profiles / playlists / emblems.
 * Migrated from Kokoro project → Pokke Users (llkatqhbganntyhppchl).
 */
object KokoroConfig {
    const val SUPABASE_URL = "https://llkatqhbganntyhppchl.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_Afmf4tdJtcBErimeAN6abg_ZtbGKInb"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxsa2F0cWhiZ2FubnR5aHBwY2hsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4ODI4NTMsImV4cCI6MjEwOTQ1ODg1M30.rJkAJVMGqTeYV9ToEAEH25JhPOjOPZyeoPPakReuuT8"

    const val PROFILES = "/rest/v1/profiles"
    const val PLAYLISTS = "/rest/v1/playlists"
    const val FAVORITES = "/rest/v1/favorites"
    const val USER_SETTINGS = "/rest/v1/user_settings"
    const val TORI_USER_APP_STATE = "/rest/v1/tori_user_app_state"
    const val USER_EMBLEMS = "/rest/v1/user_emblems"
    const val MUSIC_SOURCES = "/rest/v1/music_sources"
    const val AUTH_SIGNUP = "/auth/v1/signup"
    const val AUTH_TOKEN = "/auth/v1/token?grant_type=password"
    const val AUTH_REFRESH = "/auth/v1/token?grant_type=refresh_token"
    const val AUTH_USER = "/auth/v1/user"
    const val AUTH_LOGOUT = "/auth/v1/logout"
    const val PLAYLIST_SONGS = "/rest/v1/playlist_songs"
}
