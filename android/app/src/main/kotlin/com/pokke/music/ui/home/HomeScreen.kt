package com.pokke.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.MultiMusicRepository
import com.pokke.music.player.PlayerController
import com.pokke.music.ui.liquid.MoshiiGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

private val MoshiiAccent = Color(0xFFFC3C44)

/** Home translated from Moshii's compact-header + horizontal-rail composition.
 * Data remains connected to Tori's enabled music sources and PlayerController.
 */
@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onScrollStateChanged: (Boolean) -> Unit = {},
    authRepository: AuthRepository
) {
    val repository = remember { MultiMusicRepository() }
    var recent by remember { mutableStateOf<List<Song>>(emptyList()) }
    var forYou by remember { mutableStateOf<List<Song>>(emptyList()) }
    var popular by remember { mutableStateOf<List<Song>>(emptyList()) }
    var trending by remember { mutableStateOf<List<Song>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Song>>(emptyList()) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var headerCollapsed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferences(context) }
    val glass by preferences.liquidGlassEnabled.collectAsState(false)

    LaunchedEffect(Unit) {
        loading = true
        val results = withContext(Dispatchers.IO) {
            val catalog = repository.getRecentAll(100).getOrDefault(emptyList())
            val personalizedFallback = repository.searchAll("top hits", 24).getOrDefault(emptyList())
            listOf(catalog, personalizedFallback)
        }
        val catalog = results[0]
        recent = catalog.take(24)
        forYou = results[1].ifEmpty { recent }
        // Moshii builds these editorial rails from the available catalogue.
        popular = catalog.shuffled().take(24)
        trending = catalog.take(24)
        newReleases = catalog.asReversed().take(24)
        authRepository.getProfile().onSuccess { avatar = it?.avatarUrl }
        if (avatar.isNullOrBlank()) {
            val nick = preferences.onboardingNickname.firstOrNull()
            if (!nick.isNullOrBlank()) avatar = null
        }
        loading = false
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val collapsed = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        headerCollapsed = collapsed
        onScrollStateChanged(collapsed)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0C0C0E))) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                val headerHeight by animateDpAsState(if (headerCollapsed) 40.dp else 56.dp, label = "moshii-header-height")
                MoshiiGlass(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (headerCollapsed) 2.dp else 8.dp),
                    enabled = glass,
                    shape = RoundedCornerShape(18.dp),
                    opacity = 0.09f,
                    borderWidth = 0.55.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(headerHeight).padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Inicio", Modifier.weight(1f), color = Color.White, fontSize = if (headerCollapsed) 16.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
                        AnimatedVisibility(visible = !headerCollapsed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "Buscar", tint = Color.White.copy(.78f)) }
                                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Configuración", tint = Color.White.copy(.78f)) }
                                IconButton(onClick = onOpenProfile) {
                                    if (!avatar.isNullOrBlank()) {
                                        AsyncImage(avatar, "Perfil", Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.Person, "Perfil", tint = Color.White.copy(.78f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { MoshiiSectionTitle("Escuchado recientemente") }
                item { SongRail(recent, onSongClick) }
            }
            if (forYou.isNotEmpty()) {
                item { MoshiiSectionTitle("Para ti") }
                item { SongRail(forYou, onSongClick) }
            }
            if (popular.isNotEmpty()) {
                item { MoshiiSectionTitle("Popular hoy") }
                item { SongRail(popular, onSongClick) }
            }
            if (trending.isNotEmpty()) {
                item { MoshiiSectionTitle("Tendencias en Pokke") }
                item { SongRail(trending, onSongClick) }
            }
            if (newReleases.isNotEmpty()) {
                item { MoshiiSectionTitle("Novedades") }
                item { SongRail(newReleases, onSongClick) }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MoshiiAccent, strokeWidth = 2.dp)
                    }
                }
            } else if (recent.isEmpty() && forYou.isEmpty() && popular.isEmpty() && trending.isEmpty() && newReleases.isEmpty()) {
                item {
                    Text("Explora o busca música para empezar.", color = Color.White.copy(.5f), modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MoshiiSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SongRail(songs: List<Song>, onSongClick: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs.take(24), key = { "${it.id}_${it.title}" }) { song ->
            Column(
                Modifier.width(140.dp).clickable {
                    val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    PlayerController.playQueue(songs, index)
                    onSongClick(song)
                }
            ) {
                AsyncImage(
                    model = song.displayCoverUrl ?: song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF24242A)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

