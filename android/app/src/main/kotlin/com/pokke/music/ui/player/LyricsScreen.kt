package com.pokke.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricsSource
import com.pokke.music.data.model.LyricLine
import com.pokke.music.data.model.Song
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.lyrics.PokkeLyricsRepository
import com.pokke.music.player.PlayerController
import com.pokke.music.util.ShareLinks
import kotlinx.coroutines.launch

private val lyricLanguages = listOf(
    "es" to "Español", "en" to "English", "pt" to "Português", "fr" to "Français",
    "de" to "Deutsch", "it" to "Italiano", "ja" to "日本語", "ko" to "한국어",
    "zh" to "中文", "ru" to "Русский", "ar" to "العربية", "hi" to "हिन्दी",
)

@Composable
fun LyricsScreen(onBack: () -> Unit, songOverride: Song? = null) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()
    val playingSong by PlayerController.currentSong.collectAsState()
    val current = songOverride ?: playingSong
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val animIntensity by prefs.lyricsAnimIntensity.collectAsState(1f)
    val colorMode by prefs.lyricsColorMode.collectAsState("classic")
    val targetLang by prefs.lyricsTargetLang.collectAsState("es")

    var lines by remember { mutableStateOf(current?.lyrics.orEmpty()) }
    var plain by remember { mutableStateOf(current?.lyricsText) }
    var loading by remember { mutableStateOf(false) }
    var translated by remember { mutableStateOf(false) }
    var originalLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var providerName by remember { mutableStateOf("Automático") }
    var providerMenu by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(current?.id) {
        val song = current ?: return@LaunchedEffect
        lines = song.lyrics
        plain = song.lyricsText
        translated = false
        originalLines = emptyList()
        providerName = "Automático"
        loadError = null
        if (lines.isEmpty()) {
            loading = true
            val result = PokkeLyricsRepository.load(context, song)
            lines = result?.lines.orEmpty()
            if (result?.provider != null) providerName = result.provider.label
            if (lines.isEmpty() && !song.lyricsText.isNullOrBlank()) {
                plain = song.lyricsText
                lines = song.lyricsText.lines().filter(String::isNotBlank).mapIndexed { index, text -> LyricLine(index * 2500L, text) }
            }
            if (lines.isEmpty()) loadError = "No se encontraron letras en los proveedores disponibles."
            loading = false
        }
    }

    val active = remember(position, lines) { lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0) }
    LaunchedEffect(active, lines.size) {
        if (lines.isNotEmpty()) runCatching { listState.animateScrollToItem(active.coerceAtMost(lines.lastIndex)) }
    }

    fun emotionColor(text: String, selected: Boolean): Color {
        if (!selected) return Color.White.copy(0.32f)
        val t = text.lowercase()
        return when {
            anyWord(t, listOf("love", "amor", "corazón", "heart", "kiss")) -> Color(0xFFFF6B9D)
            anyWord(t, listOf("sad", "llorar", "cry", "pain", "dolor", "alone")) -> Color(0xFF7EA8FF)
            anyWord(t, listOf("fire", "fuego", "rage", "ira", "hot")) -> Color(0xFFFF8A4C)
            anyWord(t, listOf("night", "noche", "moon", "luna", "dark")) -> Color(0xFFB39DFF)
            anyWord(t, listOf("happy", "feliz", "dance", "bailar", "party")) -> Color(0xFFFFD56A)
            else -> Color.White
        }
    }

    fun chooseProvider(source: LyricsSource?) {
        val song = current ?: return
        providerName = source?.label ?: "Automático"
        translated = false
        originalLines = emptyList()
        providerMenu = false
        scope.launch {
            loading = true
            loadError = null
            val result = PokkeLyricsRepository.load(context, song, source, forceRefresh = true)
            if (current?.id == song.id) {
                lines = result?.lines.orEmpty()
                if (lines.isEmpty()) loadError = "${source?.label ?: "Los proveedores"}: no se encontraron letras para esta canción."
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(current?.title ?: "Letras", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text(current?.artist ?: "", color = Color.White.copy(0.55f), fontSize = 12.sp, maxLines = 1)
                }
                if (current != null) IconButton(onClick = {
                    ShareLinks.share(context, ShareLinks.Kind.LYRICS, current!!.id, "Letra de ${current!!.title}")
                }) { Icon(Icons.Default.Share, "Compartir letra", tint = Color.White) }
                Box {
                    Text(
                        providerName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.10f))
                            .clickable { providerMenu = true }.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        DropdownMenuItem(text = { Text("Automático · todos los proveedores") }, onClick = { chooseProvider(null) })
                        LyricsSource.offered.forEach { source ->
                            DropdownMenuItem(text = { Text(source.label) }, onClick = { chooseProvider(source) })
                        }
                    }
                }
                IconButton(enabled = lines.isNotEmpty() && !loading, onClick = {
                    scope.launch {
                        if (translated && originalLines.isNotEmpty()) {
                            lines = originalLines
                            translated = false
                            return@launch
                        }
                        val src = originalLines.ifEmpty { lines }
                        val song = current ?: return@launch
                        if (src.isEmpty()) return@launch
                        loading = true
                        originalLines = src
                        val output = PokkeLyricsRepository.translate(context, song, targetLang, src)
                        if (current?.id == song.id) {
                            if (output != null) {
                                lines = output
                                translated = true
                            } else {
                                loadError = "No se pudo traducir ahora. Comprueba tu conexión e inténtalo de nuevo."
                            }
                            loading = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Translate, "Traducir", tint = if (translated) Color(0xFF8BE9A0) else Color.White)
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lyricLanguages.forEach { (code, label) ->
                    val selected = targetLang == code
                    Text(
                        label,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color.White else Color.White.copy(0.12f))
                            .clickable {
                                scope.launch {
                                    prefs.setLyricsTargetLang(code)
                                    if (translated) {
                                        val song = current
                                        if (song != null && originalLines.isNotEmpty()) {
                                            loading = true
                                            val output = PokkeLyricsRepository.translate(context, song, code, originalLines)
                                            if (current?.id == song.id) {
                                                if (output != null) lines = output
                                                else loadError = "No se pudo traducir a $label."
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            }.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = if (translated) "Traducción · $targetLang" else "Fuente · $providerName",
                color = Color.White.copy(0.42f), fontSize = 11.sp,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
            )

            if (lines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy((14 + 10 * animIntensity).dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val selected = index == active
                        val targetColor = when (colorMode) {
                            "emotion" -> emotionColor(line.text, selected)
                            "mono" -> if (selected) Color(0xFFE8E8E8) else Color.White.copy(0.28f)
                            else -> if (selected) Color.White else Color.White.copy(0.35f)
                        }
                        val color by animateColorAsState(targetColor, tween((180 / animIntensity.coerceAtLeast(0.3f)).toInt()), label = "lyricColor")
                        val scale by animateFloatAsState(
                            if (selected) 1f + 0.06f * animIntensity else 0.96f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "lyricScale"
                        )
                        Text(
                            line.text,
                            color = color,
                            fontSize = if (selected) (25 + 4 * animIntensity).sp else 22.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = 34.sp,
                            modifier = Modifier.fillMaxWidth().scale(scale).clickable { PlayerController.seekTo(line.timeMs) }
                        )
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            loading -> "Buscando letras…"
                            loadError != null -> loadError!!
                            !plain.isNullOrBlank() -> plain!!
                            else -> "No se encontraron letras sincronizadas para esta canción. Prueba otro proveedor."
                        },
                        color = Color.White.copy(0.7f), fontSize = 17.sp, lineHeight = 27.sp,
                        textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                Slider(
                    value = if (duration > 0) position.toFloat().div(duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { PlayerController.seekTo((it * duration).toLong()) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.3f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { PlayerController.seekTo((position - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = { PlayerController.togglePlayPause() }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = { PlayerController.seekTo((position + 10000).coerceAtMost(duration)) }) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

private fun anyWord(text: String, words: List<String>) = words.any { it in text }
