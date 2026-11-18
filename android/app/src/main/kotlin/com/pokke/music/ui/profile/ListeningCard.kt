package com.pokke.music.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.LyricLine
import com.pokke.music.data.model.Song
import com.pokke.music.data.lyrics.PokkeLyricsRepository
import com.pokke.music.player.PlayerController

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun currentLyricLine(lyrics: List<LyricLine>, positionMs: Long): LyricLine? {
    if (lyrics.isEmpty()) return null
    var current: LyricLine? = null
    for (line in lyrics) {
        if (line.timeMs <= positionMs) current = line else break
    }
    return current
}

fun nextLyricTime(lyrics: List<LyricLine>, current: LyricLine?): Long? {
    if (current == null || lyrics.isEmpty()) return null
    val idx = lyrics.indexOfFirst { it.timeMs == current.timeMs && it.text == current.text }
    if (idx < 0 || idx >= lyrics.lastIndex) return null
    return lyrics[idx + 1].timeMs
}

/**
 * Karaoke: already-sung characters stay white; the "cursor" of 2 chars is colored;
 * upcoming stays dim. Progress advances through the line based on time until next line.
 */
@Composable
fun KaraokeLyricText(
    text: String,
    progressInLine: Float,
    accent: Color,
    ink: Color,
    modifier: Modifier = Modifier
) {
    val clean = text.trim().removePrefix("\"").removeSuffix("\"")
    val body = clean.replace(Regex("^\\[\\d{2}:\\d{2}(?:\\.\\d+)?]\\s*"), "")
    if (body.isEmpty()) return

    val p = progressInLine.coerceIn(0f, 1f)
    // Highlight window of 2 characters moves forward; everything before is white (sung)
    val cursor = ((body.length + 1) * p).toInt().coerceIn(0, body.length)
    val colors = listOf(accent, Color(0xFFFF9FCB), Color(0xFF8CB7FF), Color(0xFF8FE0B0), Color(0xFFE5C17A))
    val hi = colors[(cursor / 2) % colors.size]

    Text(
        text = buildAnnotatedString {
            body.forEachIndexed { i, c ->
                when {
                    i < cursor -> withStyle(
                        SpanStyle(color = Color.White, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
                    ) { append(c) }
                    i == cursor || i == cursor + 1 -> withStyle(
                        SpanStyle(color = hi, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    ) { append(c) }
                    else -> withStyle(
                        SpanStyle(color = ink.copy(alpha = 0.45f), fontStyle = FontStyle.Italic)
                    ) { append(c) }
                }
            }
        },
        fontSize = 14.sp,
        lineHeight = 20.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun ListeningCard(
    accent: Color,
    cardBg: Color,
    ink: Color,
    muted: Color,
    live: Boolean = true,
    fallbackSong: Song? = null,
    fallbackPositionMs: Long = 0L,
    fallbackDurationMs: Long = 0L,
    showLyrics: Boolean = true,
    modifier: Modifier = Modifier
) {
    val current by PlayerController.currentSong.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val livePosition by PlayerController.positionMs.collectAsState()
    val liveDuration by PlayerController.durationMs.collectAsState()

    val song = if (live) current else fallbackSong
    if (song == null) return

    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedLyrics by remember(song.id) { mutableStateOf(song.lyrics) }
    LaunchedEffect(song.id, showLyrics) {
        resolvedLyrics = song.lyrics
        if (showLyrics && resolvedLyrics.isEmpty()) {
            val fetched = PokkeLyricsRepository.load(context = context, song = song)?.lines.orEmpty()
            resolvedLyrics = fetched.ifEmpty {
                song.lyricsText.orEmpty().lines().filter(String::isNotBlank)
                    .mapIndexed { index, text -> LyricLine(index * 4000L, text) }
            }
        }
    }

    val position = if (live) livePosition else fallbackPositionMs
    val duration = if (live) liveDuration else fallbackDurationMs.coerceAtLeast(1L)

    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val lyric = remember(song.id, position, showLyrics, resolvedLyrics) {
        if (!showLyrics) null else currentLyricLine(resolvedLyrics, position)
    }
    val nextT = remember(resolvedLyrics, lyric) { nextLyricTime(resolvedLyrics, lyric) }
    val lineProgress = remember(lyric, position, nextT) {
        if (lyric == null) 0f
        else {
            val start = lyric.timeMs.toFloat()
            val end = (nextT ?: (duration.takeIf { it > 0 } ?: (start + 4000))).toFloat()
            val span = (end - start).coerceAtLeast(1f)
            ((position - start) / span).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(cardBg, cardBg.copy(alpha = 0.92f), accent.copy(alpha = 0.08f))
                )
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (playing || live) "Escuchando en Pokke" else "Escuchó recientemente",
                color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1D2A))) {
                song.displayCoverUrl?.let {
                    AsyncImage(it, "Cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live && duration > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(position), color = muted, fontSize = 11.sp)
                        Text(formatMs(duration), color = muted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (lyric != null && lyric.text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                AnimatedContent(
                    targetState = lyric.text,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "listeningCardLyric",
                ) { displayedLine ->
                    KaraokeLyricText(
                        text = displayedLine,
                        progressInLine = lineProgress,
                        accent = accent,
                        ink = ink,
                    )
                }
            }
        }
    }
}
