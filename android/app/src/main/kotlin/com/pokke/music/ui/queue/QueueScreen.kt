package com.pokke.music.ui.queue

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.player.PlayerController

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = androidx.compose.runtime.remember(context) { UserPreferences(context.applicationContext) }
    val glass by prefs.liquidGlassEnabled.collectAsState(initial = false)
    val current by PlayerController.currentSong.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val queue by PlayerController.queue.collectAsState()
    val currentIndex by PlayerController.currentIndex.collectAsState()
    val cover = current?.displayCoverUrl ?: current?.coverUrl
    // Keep previous, current and upcoming entries in one timeline regardless of catalogue.
    val entries = queue.mapIndexed { index, song -> index to song }
    val upcomingCount = (queue.size - currentIndex - 1).coerceAtLeast(0)
    val cardShape = RoundedCornerShape(24.dp)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF08080B))) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(if (glass) 76.dp else 64.dp).graphicsLayer { alpha = if (glass) .38f else .25f }
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xA908080B), Color(0xF508080B)))))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Volver", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Cola de reproducción", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${queue.size} canciones · $upcomingCount siguientes", color = Color.White.copy(alpha = .58f), fontSize = 12.sp)
                }
                if (queue.isNotEmpty()) {
                    Text("Vaciar", color = Color.White.copy(alpha = .76f), fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { PlayerController.clearQueue() }.padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }

            if (glass) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(cardShape)
                        .background(Brush.linearGradient(listOf(Color(0xB52C2D37), Color(0x85202029), Color(0xA52B2635))))
                        .border(1.dp, Color.White.copy(alpha = .16f), cardShape)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    CurrentTrack(current, isPlaying)
                }
            } else {
                CurrentTrack(current, isPlaying)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("A CONTINUACIÓN", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Spacer(Modifier.width(9.dp))
                Text("$upcomingCount", color = Color.White.copy(alpha = .48f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(if (glass) "LIQUID GLASS" else "ESTÁNDAR", color = Color.White.copy(alpha = .35f), fontSize = 9.sp, letterSpacing = .8.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (entries.isEmpty()) {
                    item {
                        Text("Tu cola está vacía. Añade canciones desde cualquier catálogo de Pokke.",
                            color = Color.White.copy(alpha = .48f), fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp))
                    }
                }
                items(entries, key = { (index, song) -> "${index}_${song.id}" }) { (index, song) ->
                    QueueSongRow(
                        song = song,
                        active = index == currentIndex,
                        playing = index == currentIndex && isPlaying,
                        glass = glass,
                        onClick = { PlayerController.playQueueItem(index) },
                        onRemove = if (index != currentIndex) ({ PlayerController.removeQueueItem(index) }) else null,
                        onMoveUp = if (index > currentIndex + 1) ({ PlayerController.moveQueueItem(index, index - 1) }) else null,
                        onMoveDown = if (index > currentIndex && index < queue.lastIndex) ({ PlayerController.moveQueueItem(index, index + 1) }) else null
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerController.playPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { PlayerController.togglePlayPause() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Reproducir o pausar", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { PlayerController.playNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun CurrentTrack(song: Song?, isPlaying: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
        Text("REPRODUCIENDO AHORA", color = Color.White.copy(alpha = .52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        if (song == null) {
            Text("No hay ninguna canción activa", color = Color.White.copy(alpha = .48f), fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF292932))) {
                    val artwork = song.displayCoverUrl ?: song.coverUrl
                    if (!artwork.isNullOrBlank()) AsyncImage(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color(0xFFB7F0C4), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = .58f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFB7F0C4), modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun QueueSongRow(
    song: Song,
    active: Boolean,
    playing: Boolean,
    glass: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    val targetAlpha = if (active) 1f else .68f
    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(220), label = "queueRowAlpha")
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .then(if (active && glass) Modifier.background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .13f), shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp).graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292929))) {
            val artwork = song.displayCoverUrl ?: song.coverUrl
            if (!artwork.isNullOrBlank()) AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (active) Color(0xFFB7F0C4) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = .52f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Text("EN CURSO", color = Color(0xFFB7F0C4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        if (onMoveUp != null) IconButton(onClick = onMoveUp) { Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", tint = Color.White.copy(alpha = .62f), modifier = Modifier.size(18.dp)) }
        if (onMoveDown != null) IconButton(onClick = onMoveDown) { Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", tint = Color.White.copy(alpha = .62f), modifier = Modifier.size(18.dp)) }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Color.White.copy(alpha = .62f), modifier = Modifier.size(18.dp)) }
    }
}
