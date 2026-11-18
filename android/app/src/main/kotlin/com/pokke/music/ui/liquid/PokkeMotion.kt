package com.pokke.music.ui.liquid

import android.view.HapticFeedbackConstants
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.plugins.PluginRepository
import com.pokke.music.ui.motion.MotionSettings
import com.pokke.music.ui.motion.ToriMotionEngine

/** Press feedback routed through Tori's shared Motion Engine. */
@Composable
fun Modifier.pokkePressMotion(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f
): Modifier {
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = androidx.compose.runtime.remember(context) { UserPreferences(context) }
    val pluginRepository = androidx.compose.runtime.remember(context) { PluginRepository(context) }
    val pluginMotion = androidx.compose.runtime.remember(context) { pluginRepository.activeMotionPreset() }
    val pluginClickSound = androidx.compose.runtime.remember(context) { pluginRepository.activeClickSound() }
    val storedMotionSettings by preferences.motionSettings.collectAsState(initial = MotionSettings())
    val motionSettings = ToriMotionEngine.effectiveSettings(context, storedMotionSettings)
    val pressed by interactionSource.collectIsPressedAsState()
    val motionAllowed = enabled && ToriMotionEngine.isMotionEnabled(motionSettings)
    LaunchedEffect(pressed, motionSettings.haptics, motionAllowed) {
        if (pressed && motionAllowed && motionSettings.haptics) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled && pluginClickSound?.isFile == true) {
            runCatching {
                MediaPlayer.create(context, Uri.fromFile(pluginClickSound))?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            }
        }
    }
    val requestedScale = (pluginMotion?.optDouble("pressScale", pressedScale.toDouble())?.toFloat() ?: pressedScale)
        .coerceIn(0.90f, 0.99f)
    val pluginDamping = (pluginMotion?.optDouble("dampingRatio", Spring.DampingRatioNoBouncy.toDouble())?.toFloat()
        ?: Spring.DampingRatioNoBouncy).coerceIn(0.15f, 1f)
    val pluginStiffness = (pluginMotion?.optDouble("stiffness", 520.0)?.toFloat() ?: 520f).coerceIn(80f, 1200f)
    val scale by animateFloatAsState(
        targetValue = if (motionAllowed && pressed) ToriMotionEngine.pressScale(motionSettings, requestedScale) else 1f,
        animationSpec = if (motionAllowed && pluginMotion != null) spring(
            dampingRatio = pluginDamping,
            stiffness = pluginStiffness * motionSettings.speed.coerceIn(0.5f, 1.8f)
        ) else ToriMotionEngine.pressSpring(motionSettings),
        label = "tori-motion-press"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
