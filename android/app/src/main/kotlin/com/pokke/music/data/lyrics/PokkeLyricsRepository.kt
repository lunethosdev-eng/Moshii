package com.pokke.music.data.lyrics

import android.content.Context
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.pokke.music.data.model.LyricLine
import com.pokke.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pokke adapter over BitChord's real provider implementations. */
object PokkeLyricsRepository {
    data class Result(val lines: List<LyricLine>, val provider: LyricsSource?)

    suspend fun load(
        context: Context,
        song: Song,
        provider: LyricsSource? = null,
        forceRefresh: Boolean = false,
    ): Result? {
        val cacheKey = "original|${song.id}|${song.youtubeId.orEmpty()}|${song.title}|${song.artist}|${provider?.name ?: "auto"}"
        if (!forceRefresh) {
            LyricsCache.get(context, cacheKey)?.let { cached ->
                return Result(cached.lines, cached.provider?.let { runCatching { LyricsSource.valueOf(it) }.getOrNull() })
            }
        }

        val supplied = song.lyrics.takeIf { it.isNotEmpty() }
        if (supplied != null && provider == null) {
            LyricsCache.put(context, cacheKey, supplied, "Pokke")
            return Result(supplied, null)
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                LyricsRepository.lyrics(
                    videoId = song.youtubeId ?: song.id,
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.durationSeconds * 1000L,
                    album = song.album,
                    sources = provider?.let { setOf(it) } ?: LyricsSource.offered.toSet(),
                    order = provider?.let { listOf(it) } ?: LyricsSource.offered,
                    prioritizeSyllableSync = false,
                )
            }.getOrNull()
        } ?: return null

        val lines = result.lines.map { LyricLine(it.timeMs, it.text) }.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        LyricsCache.put(context, cacheKey, lines, result.source.name)
        return Result(lines, result.source)
    }

    suspend fun translate(
        context: Context,
        song: Song,
        targetLanguage: String,
        sourceLines: List<LyricLine>,
    ): List<LyricLine>? {
        if (sourceLines.isEmpty()) return null
        val key = "translation|${song.id}|${song.title}|${song.artist}|$targetLanguage|${sourceLines.hashCode()}"
        LyricsCache.get(context, key)?.let { return it.lines }
        val translated = LyricsTranslator.translateLines(sourceLines.map { it.text }, targetLanguage)
        if (translated.size != sourceLines.size || translated.indices.all { translated[it] == sourceLines[it].text }) return null
        val output = sourceLines.mapIndexed { index, line -> line.copy(text = translated[index]) }
        LyricsCache.put(context, key, output, "translation:$targetLanguage")
        return output
    }
}
