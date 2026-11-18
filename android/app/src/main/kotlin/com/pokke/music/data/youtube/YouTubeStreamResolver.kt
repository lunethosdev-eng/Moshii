package com.pokke.music.data.youtube

import com.music.bitchord.data.innertube.StreamResolver

/** Pokke adapter for BitChord's proven InnerTubeX + NewPipe stream resolver. */
object YouTubeStreamResolver {
    suspend fun resolve(videoId: String): String {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Identificador de YouTube no válido" }
        return StreamResolver.resolve(videoId)
    }
}
