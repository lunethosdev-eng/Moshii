package com.pokke.music

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pokke.music.player.PlayerController
import com.pokke.music.player.PokkeAndroidPlayerHost
import com.pokke.music.player.PokkeCanvasPlayerHost
import com.pokke.music.ui.navigation.PokkeNavHost
import com.pokke.music.ui.liquid.GlassBackdropHost
import com.pokke.music.data.preferences.UserPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.ui.onboarding.OnboardingScreen
import androidx.compose.ui.platform.LocalContext
import com.pokke.music.ui.theme.PokkeTheme

class ToriFeatureActivity : ComponentActivity() {
    private var pluginDeepLinkUri by mutableStateOf<Uri?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pluginDeepLinkUri = intent?.data?.takeIf { it.host == "plugin" }
        handleDiscordCallback(intent?.data)
        PlayerController.init(this)
        PokkeAndroidPlayerHost.install(applicationContext)
        PokkeCanvasPlayerHost.install(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            PokkeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val prefs = UserPreferences(context)
                    val glass by prefs.liquidGlassEnabled.collectAsState(false)
                    val performance by prefs.liquidPerformanceMode.collectAsState(true)
                    val onboardingComplete by prefs.onboardingCompleted.collectAsState(false)
                    val scope = rememberCoroutineScope()
                    val authRepository = androidx.compose.runtime.remember(context) { AuthRepository(context) }
                    // A single root capture feeds all glass surfaces; TextureView Canvas avoids SurfaceView capture conflicts.
                    GlassBackdropHost(enabled = glass, performanceMode = performance) {
                        if (onboardingComplete) {
                            PokkeNavHost(
                                pluginDeepLinkUri = pluginDeepLinkUri,
                                onPluginDeepLinkHandled = { pluginDeepLinkUri = null },
                                onRestartOnboarding = { scope.launch { prefs.reopenOnboarding() } }
                            )
                        } else {
                            OnboardingScreen(prefs = prefs, authRepository = authRepository, onComplete = { })
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pluginDeepLinkUri = intent.data?.takeIf { it.host == "plugin" }
        handleDiscordCallback(intent.data)
    }

    private fun handleDiscordCallback(uri: Uri?) {
        if (uri?.scheme != "pokke" || uri.host != "discord" || uri.path != "/callback") return
        val success = uri.getQueryParameter("status") == "success"
        lifecycleScope.launch {
            UserPreferences(this@ToriFeatureActivity).setDiscordPolicLinked(success)
            Toast.makeText(
                this@ToriFeatureActivity,
                if (success) "Discord se vinculó con Pokke" else "No se pudo vincular Discord",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        if (isFinishing) PlayerController.release()
        super.onDestroy()
    }
}
