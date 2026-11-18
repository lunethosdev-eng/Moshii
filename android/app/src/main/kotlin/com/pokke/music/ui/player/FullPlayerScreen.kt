package com.pokke.music.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import com.pokke.music.data.repository.LocalLibraryStore
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.model.Playlist
import com.pokke.music.data.remote.MusicMetadataApi
import com.pokke.music.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pokke.music.player.PlayerController
import com.pokke.music.ui.sheets.AudioOutputSheet
import com.pokke.music.ui.player.LocalPlayerDock
import com.pokke.music.ui.sheets.OverflowMenuSheet
import com.pokke.music.ui.liquid.LiquidSlider
import com.pokke.music.ui.backdrop.backdrops.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    authRepository: AuthRepository,
    onRequireLogin: () -> Unit
) {
    val context = LocalContext.current
    val playerDock = LocalPlayerDock.current
    var coverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val preferences = remember(context) { UserPreferences(context) }
    val blurPlayerBackground by preferences.blurPlayerBackground.collectAsState(false)
    val animatedCovers by preferences.autoAnimatedCovers.collectAsState(initial = true)
    val liquidGlassEnabled by preferences.liquidGlassEnabled.collectAsState(false)
    val liquidSliderEnabled by preferences.liquidSliderEnabled.collectAsState(true)
    val sharedPlayerBackdrop = rememberLayerBackdrop()
    val liquidPerformanceMode by preferences.liquidPerformanceMode.collectAsState(true)
    val current by PlayerController.currentSong.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val positionMs by PlayerController.positionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val playbackError by PlayerController.playbackError.collectAsState()

    val cover = current?.displayCoverUrl
    // sharedElement disabled: runtime NoSuchMethodError on BOM 2024.10 SharedTransition API
    val coverSharedModifier = Modifier

    val audioManager = remember(context) { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxMusicVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat() }
    var volume by remember(audioManager) { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMusicVolume) }
    var showAudioOutput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val s = PlayerController.currentSong.value
            val playing = PlayerController.isPlaying.value
            val pos = PlayerController.positionMs.value
            val dur = PlayerController.durationMs.value
            if (s != null) {
                authRepository.publishListening(
                    songId = s.id,
                    title = s.title,
                    artist = s.artist,
                    coverUrl = s.coverUrl,
                    audioUrl = s.audioUrl,
                    positionMs = pos,
                    durationMs = dur,
                    isPlaying = playing
                )
            }
            kotlinx.coroutines.delay(8000)
        }
    }

    var showOverflow by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableIntStateOf(30) }
    val libraryStore = remember { LocalLibraryStore(context.applicationContext) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isCurrentFavorite by remember { mutableStateOf(false) }
    var favoriteMessage by remember { mutableStateOf<String?>(null) }
    var syncedLines by remember { mutableStateOf<List<com.pokke.music.data.model.LyricLine>>(emptyList()) }
    var artistInfo by remember { mutableStateOf<MusicMetadataApi.ArtistInfo?>(null) }
    var infoLoading by remember { mutableStateOf(false) }
    val authSession by authRepository.session.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(current?.id) {
        val song = current ?: return@LaunchedEffect
        syncedLines = song.lyrics
        if (syncedLines.isEmpty()) {
            syncedLines = withContext(Dispatchers.IO) {
                runCatching { MusicMetadataApi.lyrics(song.title, song.artist, song.album, song.durationSeconds).lines }.getOrDefault(emptyList())
            }
        }
    }
    LaunchedEffect(current?.artist, showSongInfo) {
        val artist = current?.artist ?: return@LaunchedEffect
        if (showSongInfo) {
            infoLoading = true
            artistInfo = withContext(Dispatchers.IO) { runCatching { MusicMetadataApi.artistInfo(artist) }.getOrNull() }
            infoLoading = false
        }
    }

    LaunchedEffect(current?.id, authSession?.userId) {
        val songId = current?.id
        isCurrentFavorite = if (songId != null && authSession != null) authRepository.isFavorite(songId).getOrDefault(false) else false
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (blurPlayerBackground) 60.dp else 0.dp)
                    .graphicsLayer { alpha = 0.82f }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0x44000000), Color(0xD9000000), Color(0xF5090808))))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text("Playing from Buscar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(current?.id) {
                        var horizontalTravel = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    horizontalTravel > 72.dp.toPx() -> PlayerController.playPrevious()
                                    horizontalTravel < -72.dp.toPx() -> PlayerController.playNext()
                                }
                                horizontalTravel = 0f
                            },
                            onDragCancel = { horizontalTravel = 0f },
                            onHorizontalDrag = { change, amount ->
                                horizontalTravel += amount
                                change.consume()
                            },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                current?.let { song ->
                    AnimatedCoverImage(
                        song = song,
                        animatedEnabled = animatedCovers,
                        contentDescription = "Portada de ${song.title}",
                        modifier = coverSharedModifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .onGloballyPositioned { coverCoordinates = it }
                            .graphicsLayer {
                                shadowElevation = 18.dp.toPx()
                                val dock = playerDock
                                val coordinates = coverCoordinates
                                val mini = if (dock?.attached == true) dock.miniArtOnScreen() else null
                                if (dock != null && coordinates?.isAttached == true && mini != null && coordinates.size.width > 0 && coordinates.size.height > 0) {
                                    val topLeft = coordinates.localToScreen(Offset.Zero)
                                    val bottomRight = coordinates.localToScreen(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                                    val fullWidth = (bottomRight.x - topLeft.x).coerceAtLeast(1f)
                                    val fullHeight = (bottomRight.y - topLeft.y).coerceAtLeast(1f)
                                    val fullCenterX = (topLeft.x + bottomRight.x) / 2f
                                    val fullCenterY = (topLeft.y + bottomRight.y) / 2f
                                    val open = dock.openFraction()
                                    translationX = (mini.center.x - fullCenterX) * (1f - open)
                                    translationY = (mini.center.y - fullCenterY) * (1f - open)
                                    val scaleXAtMini = mini.width / fullWidth
                                    val scaleYAtMini = mini.height / fullHeight
                                    scaleX = scaleXAtMini + (1f - scaleXAtMini) * open
                                    scaleY = scaleYAtMini + (1f - scaleYAtMini) * open
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current?.title ?: "From The Start",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = current?.artist ?: "Laufey",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playbackError != null) {
                        Text(playbackError.orEmpty(), color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                IconButton(onClick = {
                    val song = current ?: return@IconButton
                    if (authSession == null) {
                        favoriteMessage = "Inicia sesión para sincronizar tus favoritos"
                    } else scope.launch {
                        val next = !isCurrentFavorite
                        authRepository.setFavorite(song, next)
                            .onSuccess { isCurrentFavorite = next }
                            .onFailure { favoriteMessage = it.localizedMessage ?: "No se pudo actualizar Kokoro" }
                    }
                }) {
                    Icon(
                        imageVector = if (isCurrentFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isCurrentFavorite) Color(0xFFFF6B81) else Color.White.copy(.8f),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val activeLyric = syncedLines.lastOrNull { it.timeMs <= positionMs }?.text
                ?: syncedLines.firstOrNull()?.text ?: "Toca para ver las letras sincronizadas"
            Text(
                text = activeLyric,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { onOpenLyrics() }.padding(vertical = 5.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                if (liquidGlassEnabled && liquidSliderEnabled) {
                    LiquidSlider(
                        value = { progress },
                        onValueChange = { PlayerController.seekToFraction(it) },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = sharedPlayerBackdrop,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        performanceMode = liquidPerformanceMode
                    )
                } else {
                    Slider(
                        value = progress,
                        onValueChange = { PlayerController.seekToFraction(it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerController.playPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            PlayerController.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { PlayerController.playNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                if (liquidGlassEnabled && liquidSliderEnabled) {
                    // Liquid volume when experimental liquid slider is ON
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(36.dp)) {
                        LiquidSlider(
                            value = { volume },
                            onValueChange = { volume = it; PlayerController.setVolume(it) },
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.001f,
                            backdrop = sharedPlayerBackdrop,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            performanceMode = liquidPerformanceMode
                        )
                    }
                } else {
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; PlayerController.setVolume(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenLyrics) {
                    Icon(Icons.Outlined.Subtitles, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { showAudioOutput = true }) {
                    Icon(Icons.Outlined.Speaker, contentDescription = "Audio Output", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Outlined.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
                }
            }

            Text(
                text = "Salida de audio de Android",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
            )
        }
    }

    if (showAudioOutput) {
        ModalBottomSheet(onDismissRequest = { showAudioOutput = false }, containerColor = if (liquidGlassEnabled) Color(0xB31C1C22) else Color(0xFF0A0A0A)) {
            AudioOutputSheet(onDismiss = { showAudioOutput = false }, glass = liquidGlassEnabled)
        }
    }
    if (showOverflow) {
            OverflowMenuSheet(
                glass = liquidGlassEnabled,

                onDismiss = { showOverflow = false },
                onShareSong = {
                    showOverflow = false
                    current?.let { song ->
                        val text = "${song.title} — ${song.artist}" + (song.audioUrl?.takeIf { it.startsWith("https://") }?.let { "\n$it" } ?: "")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir canción"))
                    }
                },
                onFavorite = {
                    showOverflow = false
                    current?.let { song ->
                        if (authSession == null) {
                            Toast.makeText(context, "Inicia sesión para sincronizar tus favoritos", Toast.LENGTH_LONG).show()
                            onRequireLogin()
                        } else scope.launch {
                            val next = !isCurrentFavorite
                            authRepository.setFavorite(song, next)
                                .onSuccess {
                                    isCurrentFavorite = next
                                    Toast.makeText(context, if (next) "Guardada en favoritos de Kokoro" else "Quitada de favoritos de Kokoro", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { Toast.makeText(context, it.localizedMessage ?: "No se pudo actualizar Kokoro", Toast.LENGTH_LONG).show() }
                        }
                    }
                },
                onDownload = {
                    showOverflow = false
                    current?.let { song -> scope.launch {
                        runCatching { libraryStore.download(song) }
                            .onSuccess { Toast.makeText(context, "Descarga iniciada", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "No se pudo descargar", Toast.LENGTH_LONG).show() }
                    } }
                },
                onPlayNext = { current?.let { PlayerController.addToQueue(it, playNext = true) }; showOverflow = false },
                onAddToQueue = { current?.let { PlayerController.addToQueue(it) }; showOverflow = false },
                onAddToPlaylist = {
                    showOverflow = false
                    if (authSession == null) {
                        favoriteMessage = "Inicia sesión para crear y sincronizar playlists"
                    } else scope.launch {
                        authRepository.getPlaylists().onSuccess { playlists = it; showPlaylistPicker = true }
                            .onFailure { favoriteMessage = it.localizedMessage ?: "No se pudieron cargar las playlists" }
                    }
                },
                onSleepTimer = { showOverflow = false; showSleepTimer = true },
                onRemoveFromQueue = { PlayerController.removeQueueItem(PlayerController.currentIndex.value); showOverflow = false },
                onSongInfo = { showOverflow = false; showSongInfo = true }
            )
    }
    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Agregar a playlist") },
            text = {
                if (playlists.isEmpty()) Text("Primero crea una playlist desde Biblioteca.")
                else Column { playlists.forEach { playlist ->
                    TextButton(onClick = {
                        current?.let { song -> scope.launch {
                            authRepository.addSongToPlaylist(playlist.id, song)
                                .onSuccess { Toast.makeText(context, "Agregada a ${playlist.name}", Toast.LENGTH_SHORT).show(); showPlaylistPicker = false }
                                .onFailure { favoriteMessage = it.localizedMessage ?: "No se pudo añadir a la playlist" }
                        } }
                    }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
                } }
            },
            confirmButton = { TextButton(onClick = { showPlaylistPicker = false }) { Text("Cerrar") } }
        )
    }
    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            title = { Text("Temporizador de apagado") },
            text = {
                Column {
                    Text("La reproducción se pausará al terminar el tiempo.")
                    Slider(value = timerMinutes.toFloat(), onValueChange = { timerMinutes = it.toInt().coerceIn(1, 180) }, valueRange = 1f..180f)
                    Text("$timerMinutes minutos")
                }
            },
            confirmButton = { TextButton(onClick = { PlayerController.startSleepTimer(timerMinutes); showSleepTimer = false; Toast.makeText(context, "Temporizador activado", Toast.LENGTH_SHORT).show() }) { Text("Iniciar", color = Color.White) } },
            dismissButton = { TextButton(onClick = { PlayerController.cancelSleepTimer(); showSleepTimer = false }) { Text("Cancelar", color = Color.White.copy(.7f)) } },
            containerColor = if (liquidGlassEnabled) Color(0xE62A292D) else Color(0xFF242328),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
    if (showSongInfo && current != null) {
        AlertDialog(
            onDismissRequest = { showSongInfo = false },
            title = { Text("Información de la canción", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = artistInfo?.image ?: current?.displayCoverUrl, contentDescription = "Artista", modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(current?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(artistInfo?.name ?: current?.artist.orEmpty(), color = Color.White.copy(.65f), fontSize = 13.sp)
                            Text("${current?.album ?: "Álbum desconocido"} · ${formatTime(durationMs)}", color = Color.White.copy(.5f), fontSize = 11.sp)
                        }
                    }
                    Text("Sobre el artista", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(if (infoLoading) "Cargando información del artista…" else artistInfo?.description ?: "No hay una biografía pública disponible para este artista.", color = Color.White.copy(.72f), fontSize = 13.sp)
                    artistInfo?.albums?.let { Text("Álbumes publicados: $it", color = Color.White.copy(.55f), fontSize = 12.sp) }
                    Text("Carátula y datos de artista: Deezer · Biografía: Wikipedia", color = Color.White.copy(.38f), fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSongInfo = false }) { Text("Cerrar", color = Color.White) } },
            containerColor = Color(0xFF242328),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
    if (favoriteMessage != null) {
        AlertDialog(
            onDismissRequest = { favoriteMessage = null },
            text = { Text(favoriteMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { favoriteMessage = null; if (authSession == null) onRequireLogin() }) { Text("Entendido") } }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
