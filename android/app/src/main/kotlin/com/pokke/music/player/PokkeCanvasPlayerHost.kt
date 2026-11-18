package com.pokke.music.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.ui.player.CanvasContentMode
import com.music.bitchord.ui.player.CanvasVideoSpec
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.pokke.music.data.preferences.UserPreferences
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlin.math.max
import kotlin.math.min

/**
 * A narrow decorator over the original PokkeAndroidPlayerHost.
 * All playback, settings, lyrics, audio and device integrations remain delegated
 * to the original host; only the Canvas video surface is enhanced here.
 */
class PokkeCanvasPlayerHost private constructor(
    private val delegate: PlayerHost,
    private val appContext: Context,
) : PlayerHost by delegate {

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) {
        val preferences = remember(appContext) { UserPreferences(appContext) }
        val repeatCanvas by preferences.canvasRepeatEnabled.collectAsState(initial = true)
        CanvasVideoSurface(
            context = appContext,
            spec = spec,
            repeatCanvas = repeatCanvas,
            modifier = modifier,
        )
    }

    companion object {
        /** Install after PokkeAndroidPlayerHost.install(), wrapping rather than editing it. */
        fun install(context: Context) {
            val originalHost = PlayerPlatform.host
            PlayerPlatform.install(PokkeCanvasPlayerHost(originalHost, context.applicationContext))
        }
    }
}

@Composable
private fun CanvasVideoSurface(
    context: Context,
    spec: CanvasVideoSpec,
    repeatCanvas: Boolean,
    modifier: Modifier,
) {
    val playback = remember(spec.canvas.url, spec.canvas.fallbackUrl) { CanvasSurfacePlayback() }
    var rendered by remember(spec.canvas.url, spec.canvas.fallbackUrl) { mutableStateOf(false) }
    val coverReveal by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (spec.revealInstantly) 0 else 420,
            easing = FastOutSlowInEasing,
        ),
        label = "canvasCoverReveal",
    )
    val currentSpec by rememberUpdatedState(spec)
    val shouldPlay = spec.isPlaying && !spec.pausedForTransition && !playback.completed

    playback.spec = spec
    playback.repeatCanvas = repeatCanvas
    playback.shouldPlay = shouldPlay
    playback.onRendered = { visible ->
        rendered = visible
        spec.onRenderedChanged(visible)
    }

    LaunchedEffect(coverReveal) {
        currentSpec.onCoverChanged(coverReveal)
    }

    AndroidView(
        modifier = modifier
            .graphicsLayer {
                // The video and still cover crossfade together. Keep the last decoded
                // frame mounted after a one-shot Canvas ends while the still returns.
                alpha = spec.presentationAlpha().coerceIn(0f, 1f) * coverReveal.coerceIn(0f, 1f)
                if (spec.bottomFade > 0f) compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val fade = spec.bottomFade.coerceIn(0f, 1f)
                val fadeEnd = (spec.bottomFadeEndPx ?: size.height).coerceIn(0f, size.height)
                if (fade > 0f && fadeEnd > 0f) {
                    val fadeStart = (1f - fade).coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.White,
                            fadeStart to androidx.compose.ui.graphics.Color.White,
                            1f to androidx.compose.ui.graphics.Color.Transparent,
                            startY = 0f,
                            endY = fadeEnd,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        factory = { viewContext ->
            TextureView(viewContext).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        playback.attach(viewContext, this@apply, Surface(texture))
                        playback.prepare(spec.canvas)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        playback.applyVideoTransform()
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        playback.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            playback.view = view
            playback.spec = spec
            playback.repeatCanvas = repeatCanvas
            playback.shouldPlay = shouldPlay
            playback.applyVideoTransform()
            playback.updatePlayback()
        },
        onRelease = { view ->
            view.surfaceTextureListener = null
            playback.release()
        },
    )

    LaunchedEffect(shouldPlay, spec.resumeDelayMs, playback) {
        if (!shouldPlay) {
            playback.pause()
        } else {
            if (spec.resumeDelayMs > 0L) delay(spec.resumeDelayMs)
            playback.start()
        }
    }

    LaunchedEffect(spec.captureStill, playback) {
        if (spec.captureStill) {
            withFrameNanos { }
            delay(35)
            val image = playback.captureFrame(max(spec.frameCapturePx, 256))
            spec.onStillCaptured(image, playback.positionMs())
        }
    }

    LaunchedEffect(spec.refreshFrameEveryMs, spec.frameCapturePx, shouldPlay, playback) {
        // The BitChord surface requests a conservative cadence. A one-second
        // ceiling keeps the ambient colours following motion without a per-frame
        // GPU readback or an expensive full-resolution capture.
        val interval = spec.refreshFrameEveryMs?.coerceAtMost(1_000L)
        if (interval != null && interval > 0L && spec.frameCapturePx > 0 && shouldPlay) {
            while (true) {
                delay(interval)
                playback.captureFrame(spec.frameCapturePx)?.let { spec.onFrameCaptured(it) }
            }
        }
    }

    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
}

private class CanvasSurfacePlayback {
    @Volatile var spec: CanvasVideoSpec? = null
    @Volatile var repeatCanvas: Boolean = true
    @Volatile var shouldPlay: Boolean = false
    @Volatile private var prepared: Boolean = false
    @Volatile private var wasPaused: Boolean = false
    @Volatile private var fallbackAttempted: Boolean = false
    @Volatile private var surface: Surface? = null
    @Volatile var view: TextureView? = null
    @Volatile private var context: Context? = null
    @Volatile private var player: MediaPlayer? = null
    var completed by mutableStateOf(false)
        private set
    var onRendered: (Boolean) -> Unit = {}

    fun attach(context: Context, view: TextureView, surface: Surface) {
        releasePlayer()
        this.context = context.applicationContext
        this.view = view
        this.surface = surface
        completed = false
        fallbackAttempted = false
    }

    fun prepare(canvas: CanvasArtwork) {
        val ctx = context ?: return
        val targetSurface = surface ?: return
        val mp = MediaPlayer()
        player = mp
        configureListeners(mp, canvas)
        runCatching {
            mp.setSurface(targetSurface)
            mp.setVolume(0f, 0f)
            mp.isLooping = repeatCanvas
            setSource(mp, ctx, canvas.url)
            mp.prepareAsync()
        }.onFailure {
            tryFallback(canvas)
        }
    }

    private fun configureListeners(mp: MediaPlayer, canvas: CanvasArtwork) {
        mp.setOnPreparedListener { preparedPlayer ->
            prepared = true
            completed = false
            val startAt = spec?.startPositionMs ?: 0L
            if (startAt > 0L) runCatching { preparedPlayer.seekTo(startAt.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            preparedPlayer.isLooping = repeatCanvas
            updatePlayback()
        }
        mp.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) {
                spec?.onAspectRatioChanged(width.toFloat() / height.toFloat())
                applyVideoTransform(width, height)
            }
        }
        mp.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                onRendered(true)
                captureFrame(spec?.frameCapturePx ?: 0)?.let { spec?.onFrameCaptured?.invoke(it) }
            }
            false
        }
        mp.setOnErrorListener { _, _, _ ->
            if (!fallbackAttempted && !canvas.fallbackUrl.isNullOrBlank()) {
                onRendered(false)
                tryFallback(canvas)
            } else {
                prepared = false
                onRendered(false)
            }
            true
        }
        mp.setOnCompletionListener {
            if (!repeatCanvas) {
                completed = true
                prepared = false
                onRendered(false)
            }
        }
    }

    private fun tryFallback(canvas: CanvasArtwork) {
        val fallback = canvas.fallbackUrl?.takeIf { it.isNotBlank() } ?: run {
            prepared = false
            onRendered(false)
            return
        }
        val mp = player ?: return
        fallbackAttempted = true
        prepared = false
        runCatching {
            mp.reset()
            configureListeners(mp, canvas)
            mp.setSurface(surface)
            mp.setVolume(0f, 0f)
            mp.isLooping = repeatCanvas
            setSource(mp, context ?: return, fallback)
            mp.prepareAsync()
        }.onFailure { onRendered(false) }
    }

    private fun setSource(mp: MediaPlayer, context: Context, rawUrl: String) {
        val uri = Uri.parse(rawUrl)
        val assetPath = uri.path?.substringAfter("/android_asset/", "")
            ?.takeIf { uri.scheme == "file" && it.isNotBlank() }
        if (assetPath != null) {
            context.assets.openFd(assetPath).use { descriptor ->
                mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
        } else {
            mp.setDataSource(context, uri)
        }
    }

    fun updatePlayback() {
        val mp = player ?: return
        runCatching { mp.isLooping = repeatCanvas }
        if (shouldPlay) start() else pause()
    }

    fun start() {
        val mp = player ?: return
        if (!prepared || completed) return
        val alreadyPlaying = runCatching { mp.isPlaying }.getOrDefault(false)
        if (!alreadyPlaying) {
            runCatching { mp.start() }.onSuccess {
                if (wasPaused) spec?.onResumed?.invoke()
                wasPaused = false
            }
        }
    }

    fun pause() {
        val mp = player ?: return
        if (runCatching { mp.isPlaying }.getOrDefault(false)) {
            runCatching { mp.pause() }
            wasPaused = true
        }
    }

    fun positionMs(): Long = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    fun captureFrame(maxEdge: Int): androidx.compose.ui.graphics.ImageBitmap? {
        if (maxEdge <= 0) return null
        val bitmap = runCatching { view?.bitmap }.getOrNull() ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val ratio = min(1f, maxEdge.toFloat() / max(bitmap.width, bitmap.height))
        val output = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * ratio).toInt()),
                max(1, (bitmap.height * ratio).toInt()),
                true,
            )
        } else bitmap
        return output.asImageBitmap()
    }

    fun applyVideoTransform() {
        val mp = player ?: return
        applyVideoTransform(mp.videoWidth, mp.videoHeight)
    }

    private fun applyVideoTransform(videoWidth: Int, videoHeight: Int) {
        val target = view ?: return
        val width = target.width.toFloat()
        val height = target.height.toFloat()
        if (width <= 0f || height <= 0f || videoWidth <= 0 || videoHeight <= 0) return
        val sx = width / videoWidth.toFloat()
        val sy = height / videoHeight.toFloat()
        val scale = when (spec?.contentMode) {
            CanvasContentMode.CROP -> max(sx, sy)
            CanvasContentMode.FIT_PORTRAIT -> min(sx, sy)
            else -> max(sx, sy)
        }
        val contentWidth = videoWidth * scale
        val contentHeight = videoHeight * scale
        val scaleX = contentWidth / width
        val scaleY = contentHeight / height
        val offsetX = (width - contentWidth) / 2f
        val offsetY = if (spec?.alignPortraitTop == true && spec?.contentMode == CanvasContentMode.FIT_PORTRAIT) {
            0f
        } else {
            (height - contentHeight) / 2f
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY)
            postTranslate(offsetX, offsetY)
        }
        target.setTransform(matrix)
    }

    fun release() {
        releasePlayer()
        runCatching { surface?.release() }
        surface = null
        view = null
        context = null
    }

    private fun releasePlayer() {
        val mp = player
        player = null
        prepared = false
        runCatching {
            mp?.setOnPreparedListener(null)
            mp?.setOnVideoSizeChangedListener(null)
            mp?.setOnInfoListener(null)
            mp?.setOnErrorListener(null)
            mp?.setOnCompletionListener(null)
            mp?.stop()
            mp?.reset()
            mp?.release()
        }
    }
}
