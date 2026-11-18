package com.pokke.music.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.repository.MusicRepository
import com.pokke.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MoshiiRed = Color(0xFFFC3C44)
private val MoshiiBlack = Color(0xFF0C0C0E)

@Composable
fun AlbumScreen(
    albumName: String,
    artistName: String?,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    val repository = remember { MusicRepository() }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumName, artistName) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            repository.getSongsByAlbum(albumName, artistName)
        }
        result.onSuccess { songs = it }
            .onFailure { error = it.localizedMessage ?: "No se pudo cargar el álbum"; songs = emptyList() }
        loading = false
    }

    val first = songs.firstOrNull()
    Box(Modifier.fillMaxSize().background(MoshiiBlack)) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MoshiiRed)
        } else if (songs.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "Este álbum no tiene canciones disponibles en el catálogo.", color = Color.White.copy(.65f))
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Volver", color = MoshiiRed) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 28.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MoshiiRed) }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = MoshiiRed)
                        }
                        AsyncImage(
                            model = first?.displayCoverUrl ?: first?.coverUrl,
                            contentDescription = albumName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(top = 12.dp).size(280.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color.White.copy(.08f))
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(albumName, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
                        Text(artistName?.takeIf { it.isNotBlank() } ?: first?.artist.orEmpty(), color = MoshiiRed, fontSize = 22.sp,
                            modifier = Modifier.padding(top = 3.dp))
                        Text("Álbum • ${songs.size} canciones", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(Modifier.weight(1f).height(52.dp).clickable {
                                PlayerController.playQueue(songs, 0); songs.firstOrNull()?.let(onSongClick)
                            }, shape = RoundedCornerShape(10.dp), color = Color(0x55505050)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = MoshiiRed, modifier = Modifier.size(28.dp))
                                    Text("Play", color = MoshiiRed, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(Modifier.weight(1f).height(52.dp).clickable {
                                val shuffled = songs.shuffled(); PlayerController.playQueue(shuffled, 0); shuffled.firstOrNull()?.let(onSongClick)
                            }, shape = RoundedCornerShape(10.dp), color = Color(0x55505050)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Shuffle, null, tint = MoshiiRed, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(5.dp)); Text("Shuffle", color = MoshiiRed, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    Column {
                        HorizontalDivider(color = Color(0xFF505050), thickness = .5.dp)
                        Row(Modifier.fillMaxWidth().clickable {
                            PlayerController.playQueue(songs, index); onSongClick(song)
                        }.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White.copy(.55f))
                        }
                    }
                }
            }
        }
    }
}
