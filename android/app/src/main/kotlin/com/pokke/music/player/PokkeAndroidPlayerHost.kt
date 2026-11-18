package com.pokke.music.player

import android.content.Context
import android.net.Uri
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.Song as BitChordSong
import com.music.bitchord.ui.player.CanvasVideoSpec
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.PlayerSettingsSource
import com.music.bitchord.ui.player.PokkeDefaultPlayerHost
import com.pokke.music.data.model.Song
import com.pokke.music.auth.external.ExternalAuthStore
import com.pokke.music.data.canvas.SpotifyToken
import com.pokke.music.data.canvas.SpotifyCanvasProvider
import com.pokke.music.data.canvas.ArtistCanvas
import com.pokke.music.data.canvas.ExternalCanvasRepository
import com.pokke.music.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private class CanvasPlayback {
    @Volatile var shouldPlay: Boolean = false
    @Volatile var player: MediaPlayer? = null
    @Volatile var surface: Surface? = null
    fun release() {
        runCatching { player?.setOnPreparedListener(null); player?.setOnErrorListener(null); player?.stop(); player?.reset(); player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
    }
}

/** Supplies Pokke animated cover URLs to the original BitChord player surface. */
class PokkeAndroidPlayerHost(context: Context) : PlayerHost by PokkeDefaultPlayerHost {
    private val app = context.applicationContext
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val canvasEnabledState = MutableStateFlow(true)
    @Volatile private var localCanvasEnabled: Boolean = true

    override val settings: PlayerSettingsSource = object : PlayerSettingsSource by PokkeDefaultPlayerHost.settings {
        override val animatedCanvas: StateFlow<Boolean> = canvasEnabledState
    }

    init {
        SpotifyToken.init(app) {
            ExternalAuthStore(app).sessions()
                .firstOrNull { it.provider.equals("spotify", ignoreCase = true) }
                ?.cookie?.substringAfter("sp_dc=", "")?.takeIf { it.isNotBlank() }.orEmpty()
        }
        preferenceScope.launch {
            UserPreferences(app).pokkeAnimatedCoversEnabled.collect { enabled ->
                // This preference controls only the bundled local library;
                // externally supplied animated artwork remains available.
                localCanvasEnabled = enabled
            }
        }
    }

    // volume is provided by PokkeDefaultPlayerHost (PlayerHost delegation).
    // Custom SystemVolume override removed: SystemVolume symbol is not always
    // visible to the app module from bitchord-sharedui KMP on AGP 9.

    override fun cachedCanvas(song: BitChordSong): CanvasArtwork? = canvasForSong(song)

    override suspend fun canvasFor(song: BitChordSong): CanvasArtwork? {
        // Refresh preferences on the suspending path; cachedCanvas remains a
        // cheap, non-blocking lookup for bundled clips.
        val preferences = UserPreferences(app)
        localCanvasEnabled = runCatching { preferences.pokkeAnimatedCoversEnabled.first() }.getOrDefault(true)
        val externalEnabled = runCatching { preferences.externalCanvasEnabled.first() }.getOrDefault(true)
        val spotifyEnabled = runCatching { preferences.spotifyCanvasEnabled.first() }.getOrDefault(false)
        val spotifyConnected = ExternalAuthStore(app).sessions().any { it.provider.equals("spotify", ignoreCase = true) }
        val source = artworkFor(song.videoId)
        val suppliedUrl = source?.animatedCoverUrl?.takeIf(::isVideoUrl)
        if (suppliedUrl != null) {
            return CanvasArtwork(url = suppliedUrl, title = source.title, artist = source.artist, album = source.album)
        }
        if (spotifyEnabled && spotifyConnected) {
            runCatching { SpotifyCanvasProvider.find(song.title, song.artist, song.albumName) }
                .getOrNull()?.takeIf { it.matches(song.title, song.artist, song.albumName) }?.let { return it }
        }
        if (externalEnabled) {
            ExternalCanvasRepository.find(song.title, song.artist, song.albumName)?.let { return it }
        }
        return canvasForSong(song)
    }

    private fun canvasForSong(song: BitChordSong): CanvasArtwork? {
        val source = artworkFor(song.videoId)
        val remoteUrl = source?.animatedCoverUrl?.takeIf(::isVideoUrl)
        if (remoteUrl != null) {
            return CanvasArtwork(url = remoteUrl, title = source.title, artist = source.artist, album = source.album)
        }
        if (!localCanvasEnabled) return null
        val localUri = ArtistCanvas.uriFor(app, source?.artist ?: song.artist, source?.id ?: song.videoId)
            ?: return null
        return CanvasArtwork(
            url = localUri.toString(),
            title = source?.title ?: song.title,
            artist = source?.artist ?: song.artist,
            album = source?.album,
        )
    }

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) {
        val shouldPlay = spec.isPlaying && !spec.pausedForTransition
        AndroidView(
            modifier = modifier.graphicsLayer { alpha = spec.presentationAlpha().coerceIn(0f, 1f) },
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    val playback = CanvasPlayback().also { it.shouldPlay = shouldPlay }
                    tag = playback
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(texture)
                            playback.surface = surface
                            val player = MediaPlayer()
                            playback.player = player
                            runCatching {
                                player.isLooping = true
                                player.setVolume(0f, 0f)
                                player.setSurface(surface)
                                player.setOnPreparedListener { prepared ->
                                    if (spec.startPositionMs > 0L) prepared.seekTo(spec.startPositionMs.toInt())
                                    if (playback.shouldPlay) prepared.start()
                                    spec.onRenderedChanged(true)
                                }
                                player.setOnErrorListener { _, _, _ -> spec.onRenderedChanged(false); true }
                                val mediaUri = Uri.parse(spec.canvas.url)
                                val assetPath = mediaUri.path?.substringAfter("/android_asset/", "")?.takeIf { mediaUri.scheme == "file" && it.isNotBlank() }
                                if (assetPath != null) {
                                    viewContext.assets.openFd(assetPath).use { descriptor ->
                                        player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                                    }
                                } else {
                                    player.setDataSource(viewContext, mediaUri)
                                }
                                player.prepareAsync()
                            }.onFailure {
                                playback.release()
                                spec.onRenderedChanged(false)
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            playback.release()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            update = { texture ->
                val playback = texture.tag as? CanvasPlayback
                if (playback != null) {
                    playback.shouldPlay = shouldPlay
                    val player = playback.player
                    val isPlaying = runCatching { player?.isPlaying == true }.getOrDefault(false)
                    if (shouldPlay && player != null && !isPlaying) {
                        runCatching { player.start() }
                        spec.onResumed()
                    } else if (!shouldPlay && player != null && isPlaying) {
                        runCatching { player.pause() }
                    }
                }
            },
            onRelease = { texture ->
                texture.surfaceTextureListener = null
                (texture.tag as? CanvasPlayback)?.release()
                texture.tag = null
            },
        )
    }

    companion object {
        private val artworkByVideoId = object : LinkedHashMap<String, Song>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>?): Boolean = size > 128
        }

        private fun artworkFor(id: String): Song? = synchronized(artworkByVideoId) { artworkByVideoId[id] }

        fun register(song: Song) = synchronized(artworkByVideoId) {
            // Keep both identifiers: queue entries may arrive keyed by the
            // database id while the shared player asks by YouTube video id.
            artworkByVideoId[song.id] = song
            song.youtubeId?.takeIf(String::isNotBlank)?.let { artworkByVideoId[it] = song }
        }

        private fun isVideoUrl(url: String): Boolean {
            val path = runCatching { Uri.parse(url).lastPathSegment.orEmpty() }.getOrDefault("").lowercase()
            return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8")
        }

        fun install(context: Context) {
            PlayerPlatform.install(PokkeAndroidPlayerHost(context))
        }
    }
}
