package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.MixTransitionView
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TransitionWindow
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Safe Android host defaults used when Pokke has not installed optional BitChord integrations. */
object PokkeDefaultPlayerHost : PlayerHost {
    override val settings: PlayerSettingsSource = object : PlayerSettingsSource {
        override val animatedCanvas = MutableStateFlow(false)
        override val audioQualityCellular = MutableStateFlow(AudioQuality.MEDIUM)
        override val audioQualityWifi = MutableStateFlow(AudioQuality.HIGH)
        override val canvasOverCellular = MutableStateFlow(false)
        override val fullBleedArtwork = MutableStateFlow(true)
        override val hideSongStatus = MutableStateFlow(false)
        override val showQueueHistory = MutableStateFlow(true)
        override val hideVolumeBar = MutableStateFlow(false)
        override val hideStatusBar = MutableStateFlow(false)
        override val lastPlayerScreen = MutableStateFlow(LastPlayerScreen.MAIN)
        override val playerTheme = MutableStateFlow(PlayerTheme.DEFAULT)
        override val lyricsBlur = MutableStateFlow(true)
        override val lyricsOffsetMs = MutableStateFlow(0)
        override val lyricsSourceOrder = MutableStateFlow(LyricsSource.offered)
        override val meteredConnection = MutableStateFlow<Boolean?>(null)
        override val streamCeilingOverride = MutableStateFlow<AudioQuality?>(null)
        override val preferUsbDac = MutableStateFlow(false)
        override val prioritizeSpotifyCanvas = MutableStateFlow(false)
        override val reduceAnimation = MutableStateFlow(false)
        override val reduceDynamicBlur = MutableStateFlow(false)
        override val disableBlendEffects = MutableStateFlow(false)
        override val showNerdStats = MutableStateFlow(false)
        override val showListenTogetherButton = MutableStateFlow(false)
        override val smartAnalysis = MutableStateFlow(SmartAnalysis())
        override val automixDetail = MutableStateFlow<String?>(null)
        override val smartFadeEnabled = MutableStateFlow(false)
        override val smartMixInProgress = MutableStateFlow(false)
        override val smartMixBlend = MutableStateFlow<MixBlend?>(null)
        override val smartMixView = MutableStateFlow<MixTransitionView?>(null)
        override val smartMixProgress = MutableStateFlow(0f)
        override val smartMixPreview = MutableStateFlow<MixTransitionView?>(null)
        override val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)
        override val spotifyCanvasAutoHide = MutableStateFlow(false)
        override val syncedLyrics = MutableStateFlow(true)
        override val translationLanguage = MutableStateFlow("es")
        override val versionAlignmentInProgress = MutableStateFlow(false)

        override fun setLastPlayerScreen(value: LastPlayerScreen) { lastPlayerScreen.value = value }
        override fun setLyricsOffsetMs(value: Int) { lyricsOffsetMs.value = value.coerceIn(-5_000, 5_000) }
        override fun setMixViewOpen(open: Boolean) = Unit
    }

    private val volumeLevel = MutableStateFlow(1f)
    override val volume: SystemVolume = object : SystemVolume {
        override val level: StateFlow<Float> = volumeLevel
        override fun set(level: Float) { volumeLevel.value = level.coerceIn(0f, 1f) }
        override fun refresh() = Unit
    }
    override val outputFormat = MutableStateFlow(OutputFormatUi())
    override val party = MutableStateFlow(PartyUi())

    override fun cachedCanvas(song: Song): CanvasArtwork? = null
    override suspend fun canvasFor(song: Song): CanvasArtwork? = null
    @Composable override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = Unit
    @Composable override fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl
    @Composable override fun rememberAudioOutputs(): List<AudioOutputDevice> = emptyList()
    @Composable override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = onOpen
    override fun selectAudioOutput(id: Int?) = Unit
    override suspend fun translateLyrics(trackId: String, lines: List<LyricLine>, targetLanguageTag: String): LyricsTranslationResult = LyricsTranslationResult.Unavailable
    override suspend fun romanizeLyrics(trackId: String, lines: List<LyricLine>, targetLanguageTag: String): LyricsRomanizationResult = LyricsRomanizationResult.Unavailable
    override fun showMessage(message: String) = Unit
    @Composable override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) = Unit
}
