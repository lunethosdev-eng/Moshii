package com.pokke.music.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine as BitChordLyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song as BitChordSong
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.player.NowPlayingScreen
import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.MusicMetadataApi
import com.pokke.music.data.remote.CoverResolver
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.LocalLibraryStore
import com.pokke.music.player.PlayerController
import com.pokke.music.player.PokkeAndroidPlayerHost
import com.pokke.music.ui.sheets.OverflowMenuSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bridges Pokke's Media3 controller and data model into BitChord's original NowPlayingScreen. */
@Composable
fun BitChordNowPlayingHost(
    authRepository: AuthRepository,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenArtist: (artistName: String) -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val current by PlayerController.currentSong.collectAsState()
    val queue by PlayerController.queue.collectAsState()
    val queueIndex by PlayerController.currentIndex.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val positionMs by PlayerController.positionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val repeatMode by PlayerController.repeatMode.collectAsState()
    val shuffleEnabled by PlayerController.shuffleEnabled.collectAsState()
    val session by authRepository.session.collectAsState()
    val playhead = remember { PlaybackPosition() }
    var lyrics by remember(current?.id) { mutableStateOf<List<BitChordLyricLine>?>(null) }
    var favorite by remember(current?.id) { mutableStateOf(false) }
    var accountName by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val library = remember(context) { LocalLibraryStore(context.applicationContext) }

    LaunchedEffect(positionMs, isPlaying) {
        playhead.report(positionMs)
        playhead.advancing = isPlaying
    }
    LaunchedEffect(current?.id) {
        val song = current ?: return@LaunchedEffect
        val supplied = song.lyrics.map { BitChordLyricLine(timeMs = it.timeMs, text = it.text) }
        lyrics = if (supplied.isNotEmpty()) supplied else withContext(Dispatchers.IO) {
            runCatching {
                MusicMetadataApi.lyrics(song.title, song.artist, song.album, song.durationSeconds).lines
                    .map { BitChordLyricLine(timeMs = it.timeMs, text = it.text) }
            }.getOrNull()
        }
    }
    LaunchedEffect(current?.id, session?.userId) {
        val songId = current?.id
        favorite = songId != null && session != null && authRepository.isFavorite(songId).getOrDefault(false)
    }
    LaunchedEffect(session?.userId) {
        accountName = if (session == null) null else authRepository.getProfile().getOrNull()?.displayName
    }

    val song = current ?: return
    PokkeAndroidPlayerHost.register(song)
    var resolvedCover by remember(song.id) { mutableStateOf(song.displayCoverUrl ?: CoverResolver.cached(song.id)) }
    LaunchedEffect(song.id) {
        if (resolvedCover.isNullOrBlank()) {
            resolvedCover = withContext(Dispatchers.IO) { CoverResolver.resolve(song) }
        }
    }
    val bitSong = remember(song, resolvedCover) { song.toBitChordSong(resolvedCover) }
    val bitQueue = remember(queue) { queue.map { it.toBitChordSong(CoverResolver.cached(it.id)) } }
    val lyricSource = if (lyrics.isNullOrEmpty()) null else LyricsSource.LRCLIB

    fun toggleFavorite() {
        if (session == null) {
            onRequireLogin()
            Toast.makeText(context, "Inicia sesión para sincronizar tus favoritos", Toast.LENGTH_SHORT).show()
        } else scope.launch {
            val next = !favorite
            authRepository.setFavorite(song, next)
                .onSuccess { favorite = next }
                .onFailure { Toast.makeText(context, it.localizedMessage ?: "No se pudo actualizar el favorito", Toast.LENGTH_SHORT).show() }
        }
    }

    NowPlayingScreen(
        song = bitSong,
        isPlaying = isPlaying,
        isLoading = false,
        position = playhead,
        durationMs = durationMs,
        audioVersionSwitching = false,
        qualityUpgraded = false,
        queue = bitQueue,
        queueIndex = queueIndex.coerceIn(0, (bitQueue.size - 1).coerceAtLeast(0)),
        hasPrevious = queueIndex > 0 || positionMs > 3_000L,
        hasNext = queueIndex < bitQueue.lastIndex,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        autoplayEnabled = false,
        signedIn = session != null,
        accountName = accountName,
        likeStatus = if (favorite) LikeStatus.LIKE else LikeStatus.INDIFFERENT,
        onToggleLike = ::toggleFavorite,
        onPlayPause = PlayerController::togglePlayPause,
        onNext = PlayerController::playNext,
        onPrevious = PlayerController::playPrevious,
        onBlockedControl = { Toast.makeText(context, "Este control no está disponible ahora", Toast.LENGTH_SHORT).show() },
        onSeek = PlayerController::seekTo,
        onSeekFraction = PlayerController::seekToFraction,
        onToggleShuffle = PlayerController::toggleShuffle,
        onCycleRepeat = PlayerController::cycleRepeatMode,
        onToggleAutoplay = {},
        onJumpTo = PlayerController::playQueueItem,
        onRemoveFromQueue = PlayerController::removeQueueItem,
        onMoveInQueue = PlayerController::moveQueueItem,
        onClearQueue = PlayerController::clearQueue,
        onOpenMenu = { showMenu = true },
        onOpenAlbum = {},
        onOpenArtist = { _, name -> if (name.isNotBlank()) onOpenArtist(name) },
        onOpenPlaybackSource = onClose,
        onListenTogether = {},
        lyrics = lyrics,
        lyricsSource = lyricSource,
        lyricsProviderStates = if (lyricSource == null) emptyMap() else mapOf(lyricSource to LyricsProviderState.FOUND),
        onSelectLyricsProvider = {},
        lyricsUnavailable = lyrics.isNullOrEmpty(),
        lyricsOffsetOpen = false,
        onDismissLyricsOffset = {},
        windowWidth = configuration.screenWidthDp.dp,
        windowHeight = configuration.screenHeightDp.dp,
        menuOpen = { showMenu },
    )

    if (showMenu) {
        OverflowMenuSheet(
            anchorRect = null,
            onDismiss = { showMenu = false },
            onShareSong = {
                showMenu = false
                val text = "${song.title} — ${song.artist}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir canción"))
            },
            onFavorite = { showMenu = false; toggleFavorite() },
            onDownload = {
                showMenu = false
                scope.launch {
                    runCatching { library.download(song) }
                        .onSuccess { Toast.makeText(context, "Descarga añadida a la cola", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.localizedMessage ?: "No se pudo descargar", Toast.LENGTH_LONG).show() }
                }
            },
            onPlayNext = { PlayerController.addToQueue(song, playNext = true); showMenu = false },
            onAddToQueue = { PlayerController.addToQueue(song); showMenu = false },
            onAddToPlaylist = { showMenu = false; Toast.makeText(context, "Abre Biblioteca para administrar tus playlists", Toast.LENGTH_SHORT).show() },
            onSleepTimer = { PlayerController.startSleepTimer(30); showMenu = false; Toast.makeText(context, "Temporizador de 30 minutos iniciado", Toast.LENGTH_SHORT).show() },
            onRemoveFromQueue = { PlayerController.removeQueueItem(queueIndex); showMenu = false },
            onSongInfo = { showMenu = false },
        )
    }
}

private fun Song.toBitChordSong(resolvedCover: String? = null) = BitChordSong(
    videoId = youtubeId ?: id,
    title = title,
    artist = artist,
    thumbnailUrl = resolvedCover?.takeIf { it.isNotBlank() }
        ?: coverUrl?.takeIf { it.isNotBlank() }
        ?: displayCoverUrl
        ?: youtubeId?.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
    durationText = durationSeconds.takeIf { it > 0 }?.let { seconds -> "%d:%02d".format(seconds / 60, seconds % 60) },
    albumName = album,
)
