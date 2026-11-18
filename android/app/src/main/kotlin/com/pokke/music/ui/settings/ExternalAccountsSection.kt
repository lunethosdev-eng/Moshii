package com.pokke.music.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.auth.external.ExternalAuthStore
import com.pokke.music.auth.external.ExternalSession
import com.pokke.music.auth.external.SpotifyLoginScreen
import com.pokke.music.auth.external.YtMusicLoginScreen
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.repository.AuthRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Optional external providers. Discord uses official server-side OAuth2, never a WebView token. */
@Composable
fun ExternalAccountsSection(prefs: UserPreferences, authRepository: AuthRepository?) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val store = remember { ExternalAuthStore(ctx) }
    val enabled by prefs.externalAccountsEnabled.collectAsState(false)
    val spotifyOn by prefs.spotifyLoginEnabled.collectAsState(false)
    val discordOn by prefs.discordLoginEnabled.collectAsState(false)
    val discordLinked by prefs.discordPolicLinked.collectAsState(false)
    val ytOn by prefs.ytMusicLoginEnabled.collectAsState(false)
    var sessions by remember { mutableStateOf(store.sessions().filter { it.provider != "discord" }) }
    var openLogin by remember { mutableStateOf<String?>(null) }
    var discordBusy by remember { mutableStateOf(false) }
    var discordMessage by remember { mutableStateOf<String?>(null) }
    var themeBusy by remember { mutableStateOf(false) }
    var showDiscordWakeWarning by remember { mutableStateOf(false) }

    // Purge legacy Discord user-session tokens captured by the previous WebView flow.
    LaunchedEffect(Unit) { store.clearProvider("discord") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cuentas externas", style = MaterialTheme.typography.titleMedium)
        Text(
            "Spotify y YouTube Music conservan su sesión local cifrada. Discord se conecta mediante OAuth2 oficial en el servidor de Pokke.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
        )
        RowToggle("Activar cuentas externas", enabled) { scope.launch { prefs.setExternalAccountsEnabled(it) } }
        if (enabled) {
            RowToggle("Spotify login", spotifyOn) { scope.launch { prefs.setSpotifyLoginEnabled(it) } }
            RowToggle("Discord", discordOn) { scope.launch { prefs.setDiscordLoginEnabled(it) } }
            RowToggle("YT Music login", ytOn) { scope.launch { prefs.setYtMusicLoginEnabled(it) } }

            if (spotifyOn) OutlinedButton(onClick = { openLogin = "spotify" }) { Text("Conectar Spotify") }
            if (ytOn) OutlinedButton(onClick = { openLogin = "ytmusic" }) { Text("Conectar YouTube Music") }
            if (discordOn) {
                Button(
                    enabled = !discordBusy,
                    onClick = { showDiscordWakeWarning = true }
                ) { Text(if (discordBusy) "Esperando a POLIC…" else if (discordLinked) "Reconectar Discord" else "Vincular Discord con OAuth2") }
                Text(if (discordLinked) "Discord vinculado a tu cuenta Pokke." else "La autorización se realiza en Discord. Pokke nunca lee ni guarda el token privado de tu sesión.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                discordMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
            sessions.forEach { s -> Text("• ${s.provider}: ${s.label.ifBlank { "sesión" }}", fontSize = 13.sp) }
            if (authRepository != null) {
                OutlinedButton(enabled = !themeBusy, onClick = {
                    themeBusy = true
                    scope.launch {
                        authRepository.syncPolicTheme()
                            .onSuccess { discordMessage = "Tema sincronizado con POLIC." }
                            .onFailure { discordMessage = "No se pudo sincronizar el tema: ${it.message}" }
                        themeBusy = false
                    }
                }) { Text(if (themeBusy) "Sincronizando tema…" else "Sincronizar tema de mi tarjeta") }
                OutlinedButton(enabled = !themeBusy, onClick = {
                    themeBusy = true
                    scope.launch {
                        authRepository.applyPolicTheme()
                            .onSuccess { discordMessage = "Tema de POLIC aplicado al perfil de Pokke." }
                            .onFailure { discordMessage = "No se pudo recuperar el tema: ${it.message}" }
                        themeBusy = false
                    }
                }) { Text("Recuperar tema desde POLIC") }
            }
        }
    }

    if (showDiscordWakeWarning) {
        AlertDialog(
            onDismissRequest = { showDiscordWakeWarning = false },
            title = { Text("Antes de conectar Discord") },
            text = {
                Text("El servidor POLIC puede tardar entre 25 y 30 segundos en despertarse. Espera ese tiempo antes de comprobar tu perfil: si el servidor aún no está listo, tu vinculación y los datos de tu tarjeta podrían no aparecer todavía.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDiscordWakeWarning = false
                    if (authRepository == null) {
                        discordMessage = "Inicia sesión en Pokke para vincular Discord."
                    } else {
                        discordBusy = true
                        discordMessage = null
                        scope.launch {
                            // Wake Render in the background, then give its free instance 27 seconds to become ready.
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { authRepository.wakePolicServer() }
                            discordMessage = "Despertando POLIC; espera aproximadamente 27 segundos…"
                            delay(27_000)
                            authRepository.startDiscordOAuth()
                                .onSuccess { url ->
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        .onFailure { discordMessage = "No se pudo abrir Discord: ${it.message}" }
                                }
                                .onFailure { discordMessage = it.message ?: "No se pudo iniciar OAuth2" }
                            discordBusy = false
                        }
                    }
                }) { Text("OK") }
            }
        )
    }

    when (openLogin) {
        "spotify" -> SpotifyLoginScreen(onConnected = { token ->
            store.upsert(ExternalSession("spotify", "Spotify", "sp_dc=$token"))
            sessions = store.sessions().filter { it.provider != "discord" }
            openLogin = null
        })
        "ytmusic" -> YtMusicLoginScreen(
            mode = com.pokke.music.auth.external.WebSessionMode.SIGN_IN,
            onCaptured = { captured ->
                store.upsert(ExternalSession("ytmusic", "YT Music", captured.cookie))
                sessions = store.sessions().filter { it.provider != "discord" }
                openLogin = null
            }
        )
    }
}

@Composable
private fun RowToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
