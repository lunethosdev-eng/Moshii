package com.pokke.music.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap

/**
 * Tori's central motion system. UI components request motion from this engine
 * instead of defining unrelated animation curves.
 */
data class MotionSettings(
    val enabled: Boolean = true,
    val profile: String = PROFILE_BALANCED,
    val intensity: Float = 1f,
    val speed: Float = 1f,
    val depth: Float = 0.65f,
    val reflections: Boolean = true,
    val parallax: Boolean = true,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
) {
    companion object {
        const val PROFILE_FULL = "full"
        const val PROFILE_BALANCED = "balanced"
        const val PROFILE_LIGHT = "light"
    }
}

object ToriMotionEngine {
    fun effectiveSettings(context: Context, settings: MotionSettings): MotionSettings {
        val systemReduced = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0.01f
        }.getOrDefault(false)
        return if (systemReduced && !settings.reduceMotion) settings.copy(reduceMotion = true) else settings
    }

    fun isMotionEnabled(settings: MotionSettings): Boolean =
        settings.enabled && !settings.reduceMotion

    fun pressScale(settings: MotionSettings, requestedScale: Float): Float {
        if (!isMotionEnabled(settings)) return 1f
        val profileFactor = when (settings.profile) {
            MotionSettings.PROFILE_FULL -> 1f
            MotionSettings.PROFILE_LIGHT -> 0.55f
            else -> 0.8f
        }
        val amount = ((1f - requestedScale) * settings.intensity.coerceIn(0f, 1.8f) * profileFactor)
            .coerceIn(0f, 0.12f)
        return 1f - amount
    }

    fun pressSpring(settings: MotionSettings): FiniteAnimationSpec<Float> {
        if (!isMotionEnabled(settings)) return snap()
        val profileStiffness = when (settings.profile) {
            MotionSettings.PROFILE_FULL -> 420f
            MotionSettings.PROFILE_LIGHT -> 650f
            else -> 520f
        }
        val speed = settings.speed.coerceIn(0.5f, 1.8f)
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = profileStiffness * speed * speed,
            visibilityThreshold = 0.001f,
        )
    }

    fun playerSheetSpring(settings: MotionSettings): FiniteAnimationSpec<Float> {
        if (!isMotionEnabled(settings)) return snap()
        val stiffness = when (settings.profile) {
            MotionSettings.PROFILE_FULL -> 235f
            MotionSettings.PROFILE_LIGHT -> 420f
            else -> 300f
        }
        val speed = settings.speed.coerceIn(0.5f, 1.8f)
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness * speed * speed,
            visibilityThreshold = 0.5f,
        )
    }

    fun transitionDurationMillis(settings: MotionSettings, base: Int): Int {
        if (!isMotionEnabled(settings)) return 0
        val profileFactor = when (settings.profile) {
            MotionSettings.PROFILE_FULL -> 1.15f
            MotionSettings.PROFILE_LIGHT -> 0.7f
            else -> 1f
        }
        return (base / settings.speed.coerceIn(0.5f, 1.8f) * profileFactor).toInt().coerceIn(80, 900)
    }

    fun depthScale(settings: MotionSettings): Float =
        if (isMotionEnabled(settings)) settings.depth.coerceIn(0f, 1f) else 0f
}
