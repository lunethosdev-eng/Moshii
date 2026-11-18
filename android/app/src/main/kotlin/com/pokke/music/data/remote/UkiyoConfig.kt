package com.pokke.music.data.remote

/** Ukiyo music catalog hosted in the Pokke Supabase account (project Seki). */
object UkiyoConfig {
    const val SUPABASE_URL = "https://esjoifsjljvymttinyhj.supabase.co"
    // Public client key only. Never place service_role/secret keys in an Android app.
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_N5me2DAs7TrngbvqkkbqsA_UGGakGAL"

    const val SONGS = "/rest/v1/songs"
    const val ARTISTS = "/rest/v1/artists"
}
