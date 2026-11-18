package com.pokke.music.ui.liquid

import android.os.Build
import com.pokke.music.ui.backdrop.isRenderEffectSupported
import com.pokke.music.ui.backdrop.isRuntimeShaderSupported

/**
 * Hardening for Liquid Glass on mid-range / older GPUs (e.g. Galaxy A71).
 * The SIGSEGV in libhwui DamageAccumulator happens when complex lens +
 * vibrancy + full-res capture run together on constrained RenderThread.
 */
object GlassSafety {

    /** Devices / SoCs known to crash with full glass pipeline. */
    private val FORCE_PERFORMANCE_MODELS = setOf(
        "a71", "a71n", "a71nsxx", "a52", "a52s", "a32", "a22",
        "m51", "m31", "m21", "f41",
        "sm-a715", "sm-a525", "sm-a528",
        "redmi note 8", "redmi note 9", "redmi 9",
        "moto g", "moto e"
    )

    /** True when we should never run lens / chromatic aberration. */
    val preferSafeGlass: Boolean by lazy {
        if (!isRenderEffectSupported()) return@lazy true
        val model = Build.MODEL.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        FORCE_PERFORMANCE_MODELS.any { key ->
            model.contains(key) || device.contains(key) || product.contains(key)
        } || (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) // Android 12 still fragile on many OEMs
    }

    /** Whether the full (non-performance) pipeline is allowed at all. */
    fun allowFullPipeline(userWantsFull: Boolean): Boolean {
        if (!isRenderEffectSupported()) return false
        if (preferSafeGlass) return false
        return userWantsFull
    }

    /** Blur radius in dp – never exceed these on safe path. */
    fun blurRadiusDp(performance: Boolean): Float =
        when {
            preferSafeGlass || performance -> 6f
            else -> 10f
        }

    /** Resolution scale for the recorded backdrop. Lower = fewer pixels through effects. */
    fun backdropScale(performance: Boolean): Float =
        when {
            preferSafeGlass -> 0.28f
            performance -> 0.40f
            else -> 0.55f
        }

    /** Whether lens refraction is allowed. */
    fun allowLens(performance: Boolean): Boolean =
        isRuntimeShaderSupported() && !preferSafeGlass && !performance

    /** Whether chromatic aberration is allowed (expensive). */
    fun allowChromatic(performance: Boolean): Boolean =
        allowLens(performance) && !preferSafeGlass
}
