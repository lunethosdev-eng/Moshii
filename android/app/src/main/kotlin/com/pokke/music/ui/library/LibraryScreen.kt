package com.pokke.music.ui.library

import androidx.compose.animation.animateContentSize

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Playlist
import com.pokke.music.data.model.Song
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.util.ShareLinks
import com.pokke.music.data.repository.LocalLibraryStore
import com.pokke.music.data.repository.DownloadQueueManager
import com.pokke.music.ui.theme.PokkeBlack
import com.pokke.music.ui.liquid.pokkePressMotion
import kotlinx.coroutines.launch
import java.io.File

private enum class LibraryTab { DOWNLOADS, FAVORITES, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: AuthRepository,
    isVisible: Boolean = true,
    onSongClick: (Song) -> Unit = {},
    onPlayQueue: (List<Song>, Int) -> Unit = { songs, index -> songs.getOrNull(index)?.let(onSongClick) },
    onLoginRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStore = remember { LocalLibraryStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val session by repository.session.collectAsState()
    val downloads by localStore.downloads.collectAsState(initial = emptyList())
    val downloadTasks by DownloadQueueManager.get(context.applicationContext).tasks.collectAsState()
    val history by localStore.history.collectAsState(initial = emptyList())
    var favorites by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var tab by remember { mutableStateOf(LibraryTab.DOWNLOADS) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistDescription by remember { mutableStateOf("") }
    var showPlaylistStyle by remember { mutableStateOf(false) }
    var draftPrimary by remember { mutableStateOf("#6750A4") }
    var draftSecondary by remember { mutableStateOf("#26324A") }
    var draftStyle by remember { mutableStateOf("gradient") }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var draftPlaylistCover by remember { mutableStateOf("") }
    var draftPlaylistPublic by remember { mutableStateOf(false) }
    var uploadingCover by remember { mutableStateOf(false) }
    val pickPlaylistCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            uploadingCover = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("No se pudo leer la portada")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; else -> "jpg" }
                repository.uploadPublicImage("playlists", "${editingPlaylist?.id ?: "new"}_cover.$ext", bytes, mime).getOrThrow()
            }.onSuccess { draftPlaylistCover = it; error = null }
                .onFailure { error = it.localizedMessage }
            uploadingCover = false
        }
    }

    fun refreshRemote() {
        if (session == null) return
        scope.launch {
            loading = true
            error = null
            repository.getFavorites().onSuccess { favorites = it }.onFailure { error = it.localizedMessage }
            repository.getPlaylists().onSuccess { playlists = it }.onFailure { error = it.localizedMessage }
            loading = false
        }
    }

    LaunchedEffect(session?.userId, isVisible) {
        if (session == null) {
            favorites = emptyList()
            playlists = emptyList()
            selectedPlaylist = null
            playlistSongs = emptyList()
        } else if (isVisible) refreshRemote()
    }

    LaunchedEffect(selectedPlaylist?.id) {
        val selected = selectedPlaylist ?: return@LaunchedEffect
        loading = true
        repository.getPlaylistSongs(selected.id).onSuccess { playlistSongs = it }.onFailure { error = it.localizedMessage }
        loading = false
    }

    val visibleSongs = when (tab) {
        LibraryTab.DOWNLOADS -> downloads.filter { it.audioUrl?.let(::File)?.exists() == true }
        LibraryTab.FAVORITES -> favorites
        LibraryTab.HISTORY -> history
    }

    val tabSectionIndex = 4 + (if (session == null || playlists.isEmpty()) 1 else 0) + playlists.chunked(2).size

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().animateContentSize().background(PokkeBlack).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedPlaylist != null) IconButton(onClick = { selectedPlaylist = null; playlistSongs = emptyList() }) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                }
                Text(selectedPlaylist?.name ?: "Biblioteca", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (session != null && selectedPlaylist == null) IconButton(onClick = {
                    editingPlaylist = null; playlistName = ""; playlistDescription = ""; draftPlaylistCover = ""
                    draftPlaylistPublic = false; draftPrimary = "#6750A4"; draftSecondary = "#26324A"; draftStyle = "gradient"; showCreate = true
                }) {
                    Icon(Icons.Default.Add, "Crear playlist", tint = Color.White)
                }
                if (session != null) IconButton(onClick = { refreshRemote() }) {
                    Icon(Icons.Default.History, "Actualizar Kokoro", tint = Color.White.copy(.75f))
                }
            }
        }

        if (selectedPlaylist != null) {
            item {
                val playlist = selectedPlaylist!!
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        Modifier.size(132.dp).clip(RoundedCornerShape(18.dp)).background(
                            playlistBrush(playlist)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!playlist.coverUrl.isNullOrBlank()) AsyncImage(playlist.coverUrl, null, Modifier.fillMaxSize())
                        else Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(.9f), modifier = Modifier.size(52.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(playlist.description.orEmpty().ifBlank { "Tu colección, a tu manera." }, color = Color.White.copy(.65f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${playlistSongs.size} canciones", color = Color.White.copy(.55f), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (playlistSongs.isNotEmpty()) {
                                OutlinedButton(onClick = { onPlayQueue(playlistSongs, 0) }) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reproducir")
                                }
                            }
                            OutlinedButton(onClick = {
                                editingPlaylist = playlist
                                playlistName = playlist.name
                                playlistDescription = playlist.description.orEmpty()
                                draftPlaylistCover = playlist.coverUrl.orEmpty()
                                draftPlaylistPublic = playlist.isPublic
                                draftPrimary = playlist.primaryColor
                                draftSecondary = playlist.secondaryColor
                                draftStyle = playlist.visualStyle
                                showCreate = true
                            }) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Editar")
                            }
                        }
                        if (playlist.isPublic) {
                            IconButton(onClick = { ShareLinks.share(context, ShareLinks.Kind.PLAYLIST, playlist.id, playlist.name) }) {
                                Icon(Icons.Default.Share, "Compartir playlist pública", tint = Color.White)
                            }
                        }
                    }
                }
            }
            items(playlistSongs, key = { "playlist_${it.id}" }) { song ->
                SongRow(song, trailing = "Quitar", onClick = {
                    val index = playlistSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    onPlayQueue(playlistSongs, index)
                }, onTrailing = {
                    scope.launch {
                        repository.removeSongFromPlaylist(selectedPlaylist!!.id, song.id)
                            .onSuccess { playlistSongs = playlistSongs.filterNot { it.id == song.id }; refreshRemote() }
                            .onFailure { error = it.localizedMessage }
                    }
                })
            }
            if (playlistSongs.isEmpty()) item { EmptyLibraryText(if (loading) "Cargando playlist…" else "Esta playlist todavía está vacía.") }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    LibraryMenuRow("Playlists", if (session == null) "Inicia sesión para sincronizar" else "${playlists.size} listas", Icons.Default.QueueMusic) {
                        scope.launch { listState.animateScrollToItem(3) }
                    }
                    LibraryMenuRow("Descargas", "${downloads.size} en este dispositivo", Icons.Default.Download) {
                        tab = LibraryTab.DOWNLOADS
                        scope.launch { listState.animateScrollToItem(tabSectionIndex) }
                    }
                    LibraryMenuRow("Favoritos", "${favorites.size} canciones guardadas", Icons.Default.Favorite) {
                        tab = LibraryTab.FAVORITES
                        refreshRemote()
                        scope.launch { listState.animateScrollToItem(tabSectionIndex) }
                    }
                    LibraryMenuRow("Historial", "${history.size} reproducciones", Icons.Default.History) {
                        tab = LibraryTab.HISTORY
                        scope.launch { listState.animateScrollToItem(tabSectionIndex) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
                    Text("Recientes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    if (history.isEmpty()) {
                        EmptyLibraryText("Tu historial de reproducción aparecerá aquí.")
                    } else {
                        history.take(5).forEach { recentSong ->
                            SongRow(recentSong, trailing = null, onClick = { onSongClick(recentSong) }, onTrailing = {})
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tus playlists", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (session != null) TextButton(onClick = {
                        editingPlaylist = null; playlistName = ""; playlistDescription = ""; draftPlaylistCover = ""
                        draftPlaylistPublic = false; draftPrimary = "#6750A4"; draftSecondary = "#26324A"; draftStyle = "gradient"; showCreate = true
                    }) { Text("Nueva", color = Color.White) }
                }
            }
            if (session == null) item { EmptyLibraryText("Inicia sesión con Kokoro para sincronizar favoritos y playlists.") }
            else if (playlists.isEmpty()) item { EmptyLibraryText("Todavía no tienes playlists en Kokoro.") }
            items(playlists.chunked(2), key = { row -> row.joinToString { it.id } }) { rowPlaylists ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPlaylists.forEach { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedPlaylist = playlist },
                            onDelete = {
                                scope.launch {
                                    repository.deletePlaylist(playlist.id)
                                        .onSuccess { refreshRemote() }
                                        .onFailure { error = it.localizedMessage }
                                }
                            }
                        )
                    }
                    if (rowPlaylists.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Text(when (tab) { LibraryTab.DOWNLOADS -> "Descargas"; LibraryTab.FAVORITES -> "Favoritos de Kokoro"; LibraryTab.HISTORY -> "Recién escuchadas" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
            if (tab == LibraryTab.DOWNLOADS) {
                val activeTasks = downloadTasks.values.filter { it.status in setOf(
                    DownloadQueueManager.Status.QUEUED,
                    DownloadQueueManager.Status.RUNNING,
                    DownloadQueueManager.Status.PAUSED,
                    DownloadQueueManager.Status.FAILED,
                ) }.sortedBy { it.id }
                items(activeTasks, key = { "download_task_${it.id}" }) { task ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when (task.status) {
                                        DownloadQueueManager.Status.QUEUED -> "En cola"
                                        DownloadQueueManager.Status.RUNNING -> if (task.totalBytes > 0) "Descargando · ${(task.progress * 100).toInt()}%" else "Descargando…"
                                        DownloadQueueManager.Status.PAUSED -> "Pausada"
                                        DownloadQueueManager.Status.FAILED -> task.error ?: "Error de descarga"
                                        else -> ""
                                    },
                                    color = if (task.status == DownloadQueueManager.Status.FAILED) Color(0xFFFF8A80) else Color.White.copy(.6f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (task.status == DownloadQueueManager.Status.RUNNING || task.status == DownloadQueueManager.Status.QUEUED) {
                                TextButton(onClick = { DownloadQueueManager.get(context).pause(task.id) }) { Text("Pausar") }
                            } else if (task.status == DownloadQueueManager.Status.PAUSED || task.status == DownloadQueueManager.Status.FAILED) {
                                TextButton(onClick = { DownloadQueueManager.get(context).resume(task.id) }) { Text("Reanudar") }
                            }
                            TextButton(onClick = { DownloadQueueManager.get(context).cancel(task.id) }) { Text("Cancelar") }
                        }
                        if (task.status == DownloadQueueManager.Status.RUNNING) {
                            if (task.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = task.progress,
                                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                    color = Color.White,
                                    trackColor = Color.White.copy(.14f),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                    color = Color.White,
                                    trackColor = Color.White.copy(.14f),
                                )
                            }
                        }
                    }
                }
            }
            if (tab == LibraryTab.FAVORITES && session == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        EmptyLibraryText("Los favoritos se guardan en tu cuenta Kokoro, no solo en este teléfono.")
                        TextButton(onClick = onLoginRequired) { Text("Iniciar sesión", color = Color.White) }
                    }
                }
            } else if (loading && tab == LibraryTab.FAVORITES && favorites.isEmpty()) {
                item { EmptyLibraryText("Cargando favoritos de Kokoro…") }
            } else if (visibleSongs.isEmpty()) {
                item { EmptyLibraryText(when (tab) { LibraryTab.DOWNLOADS -> "Las canciones descargadas aparecerán aquí."; LibraryTab.FAVORITES -> "Todavía no has guardado canciones con ❤️."; LibraryTab.HISTORY -> "Tu historial de reproducción aparecerá aquí." }) }
            }
            items(visibleSongs, key = { "${tab.name}_${it.id}" }) { song ->
                SongRow(song, trailing = if (tab == LibraryTab.DOWNLOADS || tab == LibraryTab.FAVORITES) "Quitar" else null, onClick = { onSongClick(song) }, onTrailing = {
                    scope.launch {
                        if (tab == LibraryTab.DOWNLOADS) localStore.removeDownload(song)
                        else if (tab == LibraryTab.FAVORITES) repository.setFavorite(song, false)
                            .onSuccess { favorites = favorites.filterNot { it.id == song.id } }
                            .onFailure { error = it.localizedMessage }
                    }
                })
            }
            if (error != null) item { Text(error.orEmpty(), color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
    }

    if (showPlaylistStyle && selectedPlaylist != null) {
        val palettes = listOf(
            "#6750A4" to "#26324A", "#E85D75" to "#542B62", "#168C83" to "#123D50",
            "#D97732" to "#693B32", "#4776C7" to "#202D5A", "#B18A36" to "#493A24"
        )
        AlertDialog(
            onDismissRequest = { showPlaylistStyle = false },
            title = { Text("Personalizar playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Paleta de colores", fontWeight = FontWeight.SemiBold)
                    palettes.chunked(3).forEach { paletteRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            paletteRow.forEach { (primary, secondary) ->
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Brush.horizontalGradient(listOf(parsePlaylistColor(primary), parsePlaylistColor(secondary))))
                                        .clickable { draftPrimary = primary; draftSecondary = secondary }
                                )
                            }
                        }
                    }
                    Text("Estilo", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("gradient" to "Degradado", "solid" to "Sólido", "glass" to "Glass").forEach { (id, label) ->
                            FilterChip(selected = draftStyle == id, onClick = { draftStyle = id }, label = { Text(label) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = selectedPlaylist ?: return@TextButton
                    scope.launch {
                        repository.updatePlaylistAppearance(target.id, draftPrimary, draftSecondary, draftStyle)
                            .onSuccess {
                                val updated = target.copy(primaryColor = draftPrimary, secondaryColor = draftSecondary, visualStyle = draftStyle)
                                selectedPlaylist = updated
                                playlists = playlists.map { if (it.id == updated.id) updated else it }
                                showPlaylistStyle = false
                            }
                            .onFailure { error = it.localizedMessage }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showPlaylistStyle = false }) { Text("Cancelar") } }
        )
    }

    if (showCreate) Dialog(
        onDismissRequest = { if (!uploadingCover) showCreate = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF171A28)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (editingPlaylist == null) "Crear playlist" else "Editar playlist", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Diseña una portada y dale identidad a tu colección", color = Color.White.copy(.58f), fontSize = 12.sp)
                    }
                    TextButton(onClick = { showCreate = false }) { Text("Cerrar") }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(parsePlaylistColor(draftPrimary), parsePlaylistColor(draftSecondary)))), contentAlignment = Alignment.Center) {
                            if (draftPlaylistCover.isNotBlank()) AsyncImage(draftPlaylistCover, "Portada de playlist", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (draftPlaylistCover.isBlank()) Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(42.dp))
                                Text(if (draftPlaylistCover.isBlank()) "Tu portada" else "Portada personalizada", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(onClick = { pickPlaylistCover.launch("image/*") }, enabled = !uploadingCover, modifier = Modifier.fillMaxWidth()) {
                            if (uploadingCover) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(if (uploadingCover) "Subiendo portada…" else "Elegir portada personalizada")
                        }
                        OutlinedTextField(value = playlistName, onValueChange = { playlistName = it.take(80) }, label = { Text("Nombre de la playlist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = playlistDescription, onValueChange = { playlistDescription = it.take(500) }, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Text("Plantillas", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Aurora" to Triple("#6750A4", "#26324A", "gradient"), "Sunset" to Triple("#D45D79", "#F2A65A", "gradient"), "Mono" to Triple("#30323D", "#101116", "solid"), "Glass" to Triple("#527C91", "#A6B8C8", "glass")).forEach { (label, template) ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                                    draftPrimary = template.first; draftSecondary = template.second; draftStyle = template.third
                                }.background(Brush.linearGradient(listOf(parsePlaylistColor(template.first), parsePlaylistColor(template.second)))).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text("Colores", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            listOf("#6750A4" to "#26324A", "#D45D79" to "#F2A65A", "#327C83" to "#142F3B", "#A05C38" to "#38231E", "#30323D" to "#101116").forEach { (a, b) ->
                                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(parsePlaylistColor(a), parsePlaylistColor(b)))).clickable { draftPrimary = a; draftSecondary = b })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Playlist pública", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Solo las públicas podrán compartirse mediante enlace", color = Color.White.copy(.55f), fontSize = 11.sp)
                            }
                            Switch(checked = draftPlaylistPublic, onCheckedChange = { draftPlaylistPublic = it })
                        }
                        if (error != null) Text(error.orEmpty(), color = Color(0xFFFF9C9C), fontSize = 12.sp)
                    }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            val existing = editingPlaylist
                            val result = if (existing == null) {
                                repository.createPlaylist(playlistName, playlistDescription).mapCatching { created ->
                                    repository.updatePlaylistDetails(created.id, playlistName, playlistDescription, draftPlaylistCover.ifBlank { null }, draftPlaylistPublic).getOrThrow()
                                    repository.updatePlaylistAppearance(created.id, draftPrimary, draftSecondary, draftStyle).getOrThrow()
                                    created.copy(name = playlistName.trim(), description = playlistDescription.trim(), coverUrl = draftPlaylistCover.ifBlank { null }, isPublic = draftPlaylistPublic, primaryColor = draftPrimary, secondaryColor = draftSecondary, visualStyle = draftStyle)
                                }
                            } else {
                                repository.updatePlaylistDetails(existing.id, playlistName, playlistDescription, draftPlaylistCover.ifBlank { null }, draftPlaylistPublic).mapCatching {
                                    repository.updatePlaylistAppearance(existing.id, draftPrimary, draftSecondary, draftStyle).getOrThrow()
                                    existing.copy(name = playlistName.trim(), description = playlistDescription.trim(), coverUrl = draftPlaylistCover.ifBlank { null }, isPublic = draftPlaylistPublic, primaryColor = draftPrimary, secondaryColor = draftSecondary, visualStyle = draftStyle)
                                }
                            }
                            result.onSuccess { updated ->
                                if (existing != null) selectedPlaylist = updated
                                showCreate = false; editingPlaylist = null; error = null; refreshRemote()
                            }.onFailure { error = it.localizedMessage ?: "No se pudo guardar la playlist" }
                            loading = false
                        }
                    }, enabled = playlistName.isNotBlank() && !loading && !uploadingCover,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
                ) { Text(if (editingPlaylist == null) "Crear mi playlist" else "Guardar cambios", fontWeight = FontWeight.Bold) }
            }
        }
    }

}

@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clickable(onClick = onClick).padding(3.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(
                playlistBrush(playlist)
            ),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(playlist.coverUrl, null, Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(.9f), modifier = Modifier.size(42.dp))
            }
            if (playlist.visualStyle == "glass") {
                Box(Modifier.fillMaxSize().background(Color.White.copy(.08f)))
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                    .size(34.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(.42f))
            ) {
                Icon(Icons.Default.DeleteOutline, "Eliminar playlist", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.songCount} canciones", color = Color.White.copy(.52f), fontSize = 12.sp)
    }
}

private fun parsePlaylistColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF6750A4))

private fun playlistBrush(playlist: Playlist): Brush {
    val primary = parsePlaylistColor(playlist.primaryColor)
    val secondary = parsePlaylistColor(playlist.secondaryColor)
    return if (playlist.visualStyle == "solid") Brush.linearGradient(listOf(primary, primary))
    else Brush.linearGradient(listOf(primary, secondary))
}

@Composable
private fun LibraryMenuRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth()
            .pokkePressMotion(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFFC3C44), modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(.42f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.24f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SongRow(song: Song, trailing: String?, onClick: () -> Unit, onTrailing: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val playable = !song.audioUrl.isNullOrBlank() || !song.youtubeId.isNullOrBlank()
    Row(Modifier.fillMaxWidth().pokkePressMotion(interaction, enabled = playable).clickable(interactionSource = interaction, indication = null, enabled = playable, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = song.displayCoverUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1)
        }
        if (trailing != null) TextButton(onClick = onTrailing) { Text(trailing, color = Color.White.copy(.65f), fontSize = 12.sp) }
    }
}

@Composable
private fun EmptyLibraryText(text: String) {
    Text(text, color = Color.White.copy(.45f), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
}
