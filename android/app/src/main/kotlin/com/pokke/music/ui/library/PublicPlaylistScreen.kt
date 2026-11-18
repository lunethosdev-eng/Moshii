package com.pokke.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Playlist
import com.pokke.music.data.model.Song
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.util.ShareLinks

@Composable
fun PublicPlaylistScreen(
    playlistId: String,
    repository: AuthRepository,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(playlistId) {
        loading = true; error = null
        repository.getPublicPlaylistById(playlistId)
            .onSuccess { result -> playlist = result?.first; songs = result?.second.orEmpty(); if (result == null) error = "La playlist no existe o no es pública." }
            .onFailure { error = it.localizedMessage ?: "No se pudo cargar la playlist." }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("Playlist pública", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (playlist != null) IconButton(onClick = { ShareLinks.share(context, ShareLinks.Kind.PLAYLIST, playlistId, playlist!!.name) }) {
                Icon(Icons.Default.Share, "Compartir playlist", tint = Color.White)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = Color.White.copy(.8f)) }
            playlist != null -> {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(playlist!!.coverUrl, null, Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF292936)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist!!.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(playlist!!.description.orEmpty(), color = Color(0xFFB5B5C2), fontSize = 13.sp)
                        Text("${songs.size} canciones · Pública", color = Color(0xFF9A9AA8), fontSize = 12.sp)
                    }
                }
                if (songs.isNotEmpty()) TextButton(onClick = { onPlay(songs, 0) }, modifier = Modifier.padding(start = 16.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Text("Reproducir todo")
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(songs, index) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(song.displayCoverUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(song.title, color = Color.White, maxLines = 1); Text(song.artist, color = Color(0xFF9A9AA8), fontSize = 12.sp, maxLines = 1) }
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(.7f))
                        }
                    }
                }
            }
        }
    }
}
