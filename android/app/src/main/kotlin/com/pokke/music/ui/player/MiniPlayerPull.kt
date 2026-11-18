package com.pokke.music.ui.player

import androidx.compose.runtime.staticCompositionLocalOf

/** Receives the mini-player's upward pull and release velocity. */
interface MiniPlayerPull {
    fun drag(delta: Float)
    fun release(velocity: Float)
}

val LocalMiniPlayerPull = staticCompositionLocalOf<MiniPlayerPull?> { null }
