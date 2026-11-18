package com.pokke.music.ui.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pokke.music.ui.backdrop.Backdrop
import com.pokke.music.ui.backdrop.backdrops.layerBackdrop
import com.pokke.music.ui.backdrop.backdrops.rememberLayerBackdrop
import com.pokke.music.ui.backdrop.isRenderEffectSupported

/** Shared visual runtime state for phone and Android TV. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassPerformanceMode = staticCompositionLocalOf { true }
val LocalGlassScreenActive = staticCompositionLocalOf { true }

/**
 * Records one backdrop for the currently resumed app window.
 * Hardened against the RenderThread SIGSEGV seen on Galaxy A71 / Android 12
 * when complex lens + full-res capture run together.
 */
@Composable
fun GlassBackdropHost(
    enabled: Boolean,
    performanceMode: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleActive = when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> false
                else -> lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Never attempt capture if RenderEffect is unavailable or device is blacklisted.
    val glassSupported = isRenderEffectSupported()
    val forceSafe = GlassSafety.preferSafeGlass
    val effectivePerformance = performanceMode || forceSafe || !glassSupported

    val captureActive = enabled && glassSupported && (!effectivePerformance || lifecycleActive)

    if (!captureActive) {
        CompositionLocalProvider(
            LocalAppBackdrop provides null,
            LocalGlassEnabled provides (enabled && glassSupported),
            LocalGlassPerformanceMode provides true,
            LocalGlassScreenActive provides lifecycleActive
        ) {
            Box(modifier.fillMaxSize()) { content() }
        }
        return
    }

    // Single shared recording – avoids one GPU capture per surface.
    val source = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalAppBackdrop provides source,
        LocalGlassEnabled provides true,
        LocalGlassPerformanceMode provides effectivePerformance,
        LocalGlassScreenActive provides lifecycleActive
    ) {
        Box(modifier.fillMaxSize().layerBackdrop(source)) { content() }
    }
}
