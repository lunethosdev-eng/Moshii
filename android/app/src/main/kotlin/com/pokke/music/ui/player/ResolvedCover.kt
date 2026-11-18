package com.pokke.music.ui.player

import androidx.compose.runtime.*
import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.CoverResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resuelve el cover de una canción (servidor → YT → Deezer → iTunes)
 * y lo cachea. Úsalo en lugar de song.displayCoverUrl cuando quieras
 * evitar el placeholder de nota musical.
 */
@Composable
fun rememberResolvedCover(song: Song?): String? {
    var url by remember(song?.id) {
        mutableStateOf(song?.displayCoverUrl ?: song?.id?.let { CoverResolver.cached(it) })
    }
    LaunchedEffect(song?.id) {
        if (song == null) {
            url = null
            return@LaunchedEffect
        }
        if (!url.isNullOrBlank()) return@LaunchedEffect
        url = withContext(Dispatchers.IO) { CoverResolver.resolve(song) }
    }
    return url
}
