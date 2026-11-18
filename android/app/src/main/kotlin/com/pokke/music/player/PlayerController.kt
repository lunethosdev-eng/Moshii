package com.pokke.music.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.net.Uri
import java.io.File
import java.util.UUID
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pokke.music.data.model.Song
import com.pokke.music.data.repository.LocalLibraryStore
import com.pokke.music.data.youtube.YouTubeStreamResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-facing controller. Actual playback is owned by [PlaybackService]. */
object PlayerController {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingQueue: Pair<List<Song>, Int>? = null
    private var libraryStore: LocalLibraryStore? = null
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    private val _playbackError = MutableStateFlow<String?>(null)
    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackState()
            if (playbackState == Player.STATE_ENDED) playNext()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrentItem()
            _currentSong.value?.let { song -> scope.launch { libraryStore?.recordPlayed(song) } }
            syncPlaybackState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncQueue()
            syncCurrentItem()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackError.value = error.localizedMessage ?: "No se pudo reproducir este audio"
            _isPlaying.value = false
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            syncPlaybackState()
        }
    }

    fun init(context: Context) {
        if (libraryStore == null) libraryStore = LocalLibraryStore(context.applicationContext)
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context.applicationContext, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        controllerFuture = future
        Futures.addCallback(future, object : FutureCallback<MediaController> {
            override fun onSuccess(mediaController: MediaController?) {
                if (mediaController == null) return
                controller = mediaController
                mediaController.addListener(listener)
                syncQueue()
                syncCurrentItem()
                syncPlaybackState()
                _repeatMode.value = mediaController.repeatMode
                _shuffleEnabled.value = mediaController.shuffleModeEnabled
                startPositionTicker()
                pendingQueue?.let { (songs, index) ->
                    pendingQueue = null
                    playQueue(songs, index)
                }
            }

            override fun onFailure(t: Throwable) {
                _playbackError.value = t.localizedMessage ?: "No se pudo iniciar el reproductor"
                controllerFuture = null
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionTicker() {
        scope.launch {
            while (isActive && controller != null) {
                syncPlaybackState()
                delay(400)
            }
        }
    }

    /**
     * Media3 mediaIds identify queue entries, not catalogue tracks. A track may
     * occur more than once (or exist in more than one server), so using Song.id
     * here caused entries to collapse when the timeline was synchronized.
     * Keep the original catalogue fields in metadata extras so the unified queue
     * can be rebuilt after timeline callbacks or external MediaSession changes.
     */
    private fun toMediaItem(song: Song): MediaItem {
        val extras = Bundle().apply {
            putString("pokke.song.id", song.id)
            putString("pokke.song.title", song.title)
            putString("pokke.song.artist", song.artist)
            putString("pokke.song.album", song.album)
            putString("pokke.song.cover", song.coverUrl)
            putString("pokke.song.animated_cover", song.animatedCoverUrl)
            putString("pokke.song.audio", song.audioUrl)
            putInt("pokke.song.duration", song.durationSeconds)
            putString("pokke.song.lyrics_text", song.lyricsText)
            putString("pokke.song.lyrics_url", song.lyricsUrl)
            putString("pokke.song.genre", song.genre)
            putString("pokke.song.youtube_id", song.youtubeId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri((song.coverUrl ?: song.displayCoverUrl)?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId("pokke:${UUID.randomUUID()}")
            .setUri(song.audioUrl?.let { raw -> if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("file://") || raw.startsWith("content://")) Uri.parse(raw) else Uri.fromFile(File(raw)) } ?: Uri.EMPTY)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun songFromMediaItem(mediaItem: MediaItem): Song {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val audio = extras?.getString("pokke.song.audio")
            ?: mediaItem.localConfiguration?.uri?.toString()
        return Song(
            id = extras?.getString("pokke.song.id")?.takeIf(String::isNotBlank) ?: mediaItem.mediaId,
            title = extras?.getString("pokke.song.title") ?: metadata.title?.toString().orEmpty(),
            artist = extras?.getString("pokke.song.artist") ?: metadata.artist?.toString().orEmpty(),
            album = extras?.getString("pokke.song.album") ?: metadata.albumTitle?.toString(),
            coverUrl = extras?.getString("pokke.song.cover") ?: metadata.artworkUri?.toString(),
            animatedCoverUrl = extras?.getString("pokke.song.animated_cover"),
            audioUrl = audio,
            durationSeconds = extras?.getInt("pokke.song.duration") ?: 0,
            lyricsText = extras?.getString("pokke.song.lyrics_text"),
            lyricsUrl = extras?.getString("pokke.song.lyrics_url"),
            genre = extras?.getString("pokke.song.genre"),
            youtubeId = extras?.getString("pokke.song.youtube_id")
        )
    }

    fun playSong(song: Song) = playQueue(listOf(song), 0)

    /** Reproduce y, si la cola queda en 1, intenta rellenar con resultados relacionados (mismo artista). */
    fun playSongWithAutoplay(song: Song, related: List<Song> = emptyList()) {
        val extras = related.filter { it.id != song.id }.take(25)
        if (extras.isEmpty()) playSong(song)
        else playQueue(listOf(song) + extras, 0)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _playbackError.value = null
        scope.launch {
            val resolved = songs.map { song ->
                val localAudio = song.audioUrl?.let { raw ->
                    raw.startsWith("file://") || raw.startsWith("content://") || File(raw).isFile
                } == true
                if (localAudio) song
                else {
                    val ytId = song.youtubeId?.takeIf { it.isNotBlank() }
                        ?: song.id.removePrefix("yt_").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                    if (ytId.isNullOrBlank() || !song.audioUrl.isNullOrBlank()) song
                    else runCatching {
                        song.copy(
                            youtubeId = ytId,
                            audioUrl = YouTubeStreamResolver.resolve(ytId),
                            coverUrl = song.coverUrl
                                ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                        )
                    }.onFailure { error ->
                        _playbackError.value = "No se pudo resolver ${song.title}: ${error.localizedMessage ?: "stream no disponible"}"
                    }.getOrDefault(song)
                }
            }
            val player = controller
            if (player == null) {
                pendingQueue = resolved to startIndex
                return@launch
            }
            val playable = resolved.filter { !it.audioUrl.isNullOrBlank() }
            if (playable.isEmpty()) {
                _playbackError.value = _playbackError.value ?: "No hay audio reproducible en esta selección"
                return@launch
            }
            val requested = resolved.getOrNull(startIndex)
            val safeIndex = requested?.let { playable.indexOfFirst { item -> item.id == it.id } }
                ?.takeIf { it >= 0 } ?: 0
            _playbackError.value = null
            _queue.value = playable
            player.setMediaItems(playable.map(::toMediaItem), safeIndex, 0L)
            player.prepare()
            player.playWhenReady = true
            player.play()
            syncCurrentItem()
        }
    }

    fun addToQueue(song: Song, playNext: Boolean = false) {
        scope.launch {
            val localAudio = song.audioUrl?.let { raw -> raw.startsWith("file://") || raw.startsWith("content://") || File(raw).isFile } == true
            val ytId = song.youtubeId?.takeIf { it.isNotBlank() }
                ?: song.id.removePrefix("yt_").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            val resolved = if (localAudio || (!song.audioUrl.isNullOrBlank() && ytId.isNullOrBlank())) song
            else if (ytId.isNullOrBlank()) song
            else runCatching {
                song.copy(
                    youtubeId = ytId,
                    audioUrl = YouTubeStreamResolver.resolve(ytId),
                    coverUrl = song.coverUrl ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                )
            }.getOrElse {
                _playbackError.value = "No se pudo resolver el audio: ${it.localizedMessage ?: "error desconocido"}"
                return@launch
            }
            val player = controller ?: return@launch
            val insertAt = if (playNext) {
                (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
            } else player.mediaItemCount
            player.addMediaItem(insertAt, toMediaItem(resolved))
            syncQueue()
            syncCurrentItem()
        }
    }

    fun playQueueItem(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            player.playWhenReady = true
            player.play()
        }
    }

    fun removeQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        syncQueue()
        syncCurrentItem()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount) return
        player.moveMediaItem(from, to)
        syncQueue()
        syncCurrentItem()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        _queue.value = emptyList()
        _currentSong.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _currentIndex.value = 0
    }

    fun playNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun playPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3_000L) player.seekTo(0L)
        else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        else player.seekTo(0L)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        val target = positionMs.coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        _positionMs.value = target
    }

    fun seekToFraction(fraction: Float) {
        val duration = _durationMs.value
        if (duration > 0L) seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        _repeatMode.value = next
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        _shuffleEnabled.value = player.shuffleModeEnabled
    }

    fun setGapless(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        // Media3 gapless is handled by consecutive MediaItems without pause when enabled
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    fun pollPosition() = syncPlaybackState()

    private fun syncQueue() {
        val player = controller ?: return
        val previous = _queue.value
        val items = (0 until player.mediaItemCount).map { index ->
            val mediaItem = player.getMediaItemAt(index)
            val restored = songFromMediaItem(mediaItem)
            // Preserve fields not represented in MediaMetadata extras (for
            // example timed lyric lines) when this is an entry we already own.
            val known = previous.firstOrNull { it.id == restored.id && it.title == restored.title && it.artist == restored.artist }
            if (known != null) restored.copy(lyrics = known.lyrics) else restored
        }
        _queue.value = items
    }

    private fun syncCurrentItem() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        _currentIndex.value = index
        val mediaItem = player.currentMediaItem
        if (mediaItem == null) {
            _currentSong.value = null
            return
        }
        _currentSong.value = _queue.value.getOrNull(index) ?: songFromMediaItem(mediaItem)
    }

    private fun syncPlaybackState() {
        val player = controller ?: return
        _isPlaying.value = player.isPlaying
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        val actualDuration = player.duration
        _durationMs.value = if (actualDuration > 0L) actualDuration
            else _currentSong.value?.durationMs?.coerceAtLeast(0L) ?: 0L
        _currentIndex.value = player.currentMediaItemIndex.coerceAtLeast(0)
    }


    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        val total = minutes * 60_000L
        _sleepTimerRemainingMs.value = total
        sleepTimerJob = scope.launch {
            var remaining = total
            while (remaining > 0L) {
                val step = minOf(1_000L, remaining)
                delay(step)
                remaining -= step
                _sleepTimerRemainingMs.value = remaining
            }
            controller?.pause()
            _sleepTimerRemainingMs.value = null
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    /** Keep the MediaSession alive when the Activity is closed. */
    fun release() {
        // Deliberately do not release the MediaController here: playback must outlive the UI.
    }
}
