package com.pokke.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.MusicMetadataApi
import com.pokke.music.data.repository.MultiMusicRepository
import com.pokke.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    artistName: String,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit = { PlayerController.playSong(it) },
    onAlbumClick: (String, String) -> Unit = { _, _ -> }
) {
    var info by remember { mutableStateOf<MusicMetadataApi.ArtistInfo?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val multi = remember { MultiMusicRepository() }
    val albumGroups = remember(songs) {
        songs.filter { !it.album.isNullOrBlank() }
            .groupBy { it.album!!.trim() to it.artist }
            .map { (key, tracks) -> Triple(key.first, key.second, tracks.first()) }
            .distinctBy { (album, artist, _) -> album.lowercase() + "|" + artist.lowercase() }
    }

    LaunchedEffect(artistName) {
        loading = true
        info = withContext(Dispatchers.IO) {
            runCatching { MusicMetadataApi.artistInfo(artistName) }.getOrNull()
        }
        songs = withContext(Dispatchers.IO) {
            multi.searchAll(artistName).getOrDefault(emptyList())
                .filter { it.artist.contains(artistName, ignoreCase = true) }
                .distinctBy { it.id }
                .take(40)
        }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color(0xFF1A1A22))
                ) {
                    if (!info?.image.isNullOrBlank()) {
                        AsyncImage(
                            model = info?.image,
                            contentDescription = artistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.statusBarsPadding().padding(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(20.dp)
                    ) {
                        Text(
                            info?.name ?: artistName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        info?.albums?.let {
                            Text("$it álbumes", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                info?.description?.takeIf { it.isNotBlank() }?.let { bio ->
                    item {
                        Text(
                            "Acerca de",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Text(
                            bio,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                        )
                    }
                }

                if (albumGroups.isNotEmpty()) {
                    item {
                        Text(
                            "Álbumes",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp)
                        )
                    }
                    items(albumGroups, key = { it.first + "|" + it.second }) { group ->
                        val (albumName, albumArtist, coverSong) = group
                        Row(
                            Modifier.fillMaxWidth().clickable { onAlbumClick(albumName, albumArtist) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = coverSong.displayCoverUrl ?: coverSong.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(albumName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Álbum · $albumArtist", color = MaterialTheme.colorScheme.onSurface.copy(.55f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (songs.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Canciones",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                PlayerController.playQueue(songs, 0)
                                songs.firstOrNull()?.let(onSongClick)
                            }) {
                                Icon(Icons.Default.PlayArrow, "Reproducir", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(songs, key = { it.id }) { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    PlayerController.playQueue(songs, index)
                                    onSongClick(song)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.displayCoverUrl ?: song.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.album ?: song.artist,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (!loading) {
                    item {
                        Text(
                            "No se encontraron canciones de este artista en tu catálogo.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}
