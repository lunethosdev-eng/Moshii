package com.pokke.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.ui.player.PlayerPlatform
import com.pokke.music.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

/** Loads the last BitChord player surface before composing the protected player host. */
@Composable
fun PlayerScreenPersistenceGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { UserPreferences(context) }
    val settings = remember { PlayerPlatform.host.settings }
    var restored by remember { mutableStateOf(false) }
    val currentScreen by settings.lastPlayerScreen.collectAsState()
    LaunchedEffect(settings, preferences) {
        val stored = preferences.playerLastScreen.first()
        val screen = when (stored) {
            "LYRICS" -> LastPlayerScreen.LYRICS
            "QUEUE" -> LastPlayerScreen.QUEUE
            else -> LastPlayerScreen.MAIN
        }
        settings.setLastPlayerScreen(screen)
        restored = true
    }

    LaunchedEffect(restored, currentScreen) {
        if (restored) preferences.setPlayerLastScreen(currentScreen.name)
    }

    if (restored) content()
}
