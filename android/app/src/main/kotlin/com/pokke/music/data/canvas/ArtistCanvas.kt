package com.pokke.music.data.canvas

import android.content.Context
import android.net.Uri

object ArtistCanvas {
    private val folders = linkedMapOf(
        "bad bunny" to "bad_bunny",
        "benito" to "bad_bunny",
        "depresión sonora" to "depresion_sonora",
        "depresion sonora" to "depresion_sonora",
        "depresión" to "depresion_sonora",
        "mon laferte" to "mon_laferte",
        "laferte" to "mon_laferte",
        "laufey" to "laufey",
    )

    fun resolveFolder(artist: String?): String? {
        if (artist.isNullOrBlank()) return null
        val a = artist.lowercase().trim()
        folders.forEach { (key, folder) ->
            if (a.contains(key)) return folder
        }
        return null
    }

    fun uriFor(context: Context, artist: String?, seed: String? = null): Uri? {
        val folder = resolveFolder(artist) ?: return null
        val names = try {
            context.assets.list("canvas/$folder")
                ?.filter { it.endsWith(".mp4", true) || it.endsWith(".webm", true) }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (names.isEmpty()) return null
        val idx = if (seed.isNullOrBlank()) 0 else Math.floorMod(seed.hashCode(), names.size)
        return Uri.parse("file:///android_asset/canvas/$folder/${names[idx]}")
    }
}
