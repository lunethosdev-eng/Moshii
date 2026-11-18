package com.pokke.music.ui.onboarding

import android.content.Intent
import android.net.Uri

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.auth.external.ExternalAuthStore
import com.pokke.music.auth.external.ExternalSession
import com.pokke.music.auth.external.SpotifyLoginScreen
import com.pokke.music.auth.external.YtMusicLoginScreen
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.remote.DeezerArtist
import com.pokke.music.data.remote.DeezerArtistRepository
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.ui.auth.LoginScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF090A10)
private val Violet = Color(0xFF9B8CFF)
private val Pink = Color(0xFFFF91C8)

@Composable
fun OnboardingScreen(
    prefs: UserPreferences,
    authRepository: AuthRepository,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deezer = remember { DeezerArtistRepository() }
    val externalStore = remember { ExternalAuthStore(context) }
    val authSession by authRepository.session.collectAsState()

    var step by remember { mutableIntStateOf(0) }
    var nickname by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf<List<DeezerArtist>>(emptyList()) }
    var selectedArtists by remember { mutableStateOf<List<DeezerArtist>>(emptyList()) }
    var providers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var providerIndex by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var draftLoaded by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authRepository.restoreSession()
        val raw = prefs.onboardingDraft.first()
        runCatching {
            val draft = JSONObject(raw)
            nickname = draft.optString("nickname")
            step = draft.optInt("step", 0).coerceIn(0, 3)
            providerIndex = draft.optInt("providerIndex", 0).coerceAtLeast(0)
            providers = draft.optJSONArray("providers")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
            val saved = draft.optJSONArray("artists") ?: JSONArray()
            selectedArtists = (0 until saved.length()).mapNotNull { i ->
                val a = saved.optJSONObject(i) ?: return@mapNotNull null
                DeezerArtist(a.optLong("id"), a.optString("name"), a.optString("image").takeIf { it.isNotBlank() }, null)
            }
        }
        draftLoaded = true
    }
    LaunchedEffect(step, nickname, selectedArtists, providers, providerIndex, draftLoaded) {
        if (draftLoaded && step < 4) {
            val draft = JSONObject().apply {
                put("step", step); put("nickname", nickname); put("providerIndex", providerIndex)
                put("providers", JSONArray(providers.toList()))
                put("artists", JSONArray().apply { selectedArtists.forEach { a -> put(JSONObject().apply { put("id", a.id); put("name", a.name); put("image", a.imageUrl ?: "") }) } })
            }
            prefs.saveOnboardingDraft(draft.toString())
        }
    }

    fun goWelcome() { step = 4 }
    fun persistProviderFlags() {
        scope.launch {
            prefs.setExternalAccountsEnabled(providers.isNotEmpty())
            prefs.setSpotifyLoginEnabled("spotify" in providers)
            prefs.setYtMusicLoginEnabled("ytmusic" in providers)
            prefs.setDiscordLoginEnabled("discord" in providers)
        }
    }

    LaunchedEffect(step) {
        if (step == 4 && !finished) {
            delay(5000)
            prefs.completeOnboarding(nickname, JSONArray().apply {
                selectedArtists.forEach { a -> put(JSONObject().apply { put("id", a.id); put("name", a.name); put("image", a.imageUrl ?: "") }) }
            }.toString())
            finished = true
            onComplete()
        }
    }

    BackHandler(enabled = step in 1..3) {
        if (step == 3 && providers.isNotEmpty()) {
            if (providerIndex > 0) providerIndex-- else step = 2
        } else step--
    }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Night, Color(0xFF171328), Color(0xFF10111B))))) {
        Box(Modifier.size(280.dp).offset(x = 180.dp, y = (-100).dp).clip(CircleShape).background(Violet.copy(.13f)))
        Box(Modifier.size(230.dp).align(Alignment.BottomStart).offset(x = (-100).dp, y = 80.dp).clip(CircleShape).background(Pink.copy(.09f)))

        AnimatedContent(targetState = step, label = "pokke-onboarding-step") { page ->
            when (page) {
                0 -> OnboardingPage(title = "Primero, ¿cómo te llamamos?", subtitle = "Elige el nombre que aparecerá en tu experiencia Pokke.", page = 1, onNext = { step = 1 }, nextEnabled = nickname.trim().length >= 2) {
                    OutlinedTextField(
                        value = nickname, onValueChange = { nickname = it.take(32) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Tu apodo", color = Color.White.copy(.38f)) },
                        colors = onboardingFieldColors(), shape = RoundedCornerShape(18.dp)
                    )
                    Text("Podrás cambiarlo más adelante.", color = Color.White.copy(.48f), fontSize = 12.sp)
                }
                1 -> OnboardingPage(title = "¿Qué artistas te gustan?", subtitle = "Buscamos en Deezer para personalizar tus recomendaciones.", page = 2, onBack = { step = 0 }, onNext = { step = 2 }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it.take(70) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Buscar artistas…", color = Color.White.copy(.4f)) }, colors = onboardingFieldColors(), shape = RoundedCornerShape(16.dp))
                        Button(onClick = {
                            if (query.trim().length >= 2) scope.launch {
                                searching = true; searchError = null
                                deezer.searchArtists(query).onSuccess { artists = it }.onFailure { searchError = it.localizedMessage ?: "No se pudo buscar en Deezer" }
                                searching = false
                            }
                        }, enabled = !searching && query.trim().length >= 2, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)) { Text(if (searching) "…" else "Buscar") }
                    }
                    Text("${selectedArtists.size}/12 seleccionados", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (searchError != null) Text(searchError!!, color = Color(0xFFFF9B9B), fontSize = 12.sp)
                    if (artists.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 270.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(artists, key = { it.id }) { artist ->
                                val selected = selectedArtists.any { it.id == artist.id }
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(if (selected) .13f else .055f)).clickable {
                                    selectedArtists = if (selected) selectedArtists.filterNot { it.id == artist.id } else if (selectedArtists.size < 12) selectedArtists + artist else selectedArtists
                                }.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                    AsyncImage(artist.imageUrl, artist.name, Modifier.size(46.dp).clip(CircleShape))
                                    Text(artist.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Medium)
                                    Checkbox(checked = selected, onCheckedChange = { checked -> selectedArtists = if (checked) (selectedArtists + artist).distinctBy { it.id }.take(12) else selectedArtists.filterNot { it.id == artist.id } }, colors = CheckboxDefaults.colors(checkedColor = Violet))
                                }
                            }
                        }
                    } else if (!searching) Text("Puedes elegir artistas ahora o saltarte este paso.", color = Color.White.copy(.5f), fontSize = 12.sp)
                    if (selectedArtists.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedArtists.take(4).forEach { AsyncImage(it.imageUrl, it.name, Modifier.size(30.dp).clip(CircleShape)) }
                        if (selectedArtists.size > 4) Text("+${selectedArtists.size - 4}", color = Color.White.copy(.7f), modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                2 -> OnboardingPage(title = "Conecta tu música", subtitle = "Elige los servicios que quieras conectar. Puedes hacerlo más tarde desde Ajustes.", page = 3, onBack = { step = 1 }, onNext = { persistProviderFlags(); providerIndex = 0; step = 3 }) {
                    ProviderCard("spotify", "Spotify", "Conecta tu sesión para funciones compatibles con tu cuenta y Canvas externo.", "♫", providers, Violet) { providers = if (it in providers) providers - it else providers + it }
                    ProviderCard("ytmusic", "YouTube Music", "Permite usar tu sesión y las funciones de catálogo compatibles.", "▶", providers, Color(0xFFFF6B6B)) { providers = if (it in providers) providers - it else providers + it }
                    ProviderCard("discord", "Discord", "Conecta tu cuenta para las funciones sociales y la integración de Pokke.", "◉", providers, Color(0xFF8EA4FF)) { providers = if (it in providers) providers - it else providers + it }
                }
                3 -> {
                    if (providers.isEmpty() && authSession == null) {
                        LoginScreen(repository = authRepository, onLoginSuccess = { goWelcome() }, onContinueAsGuest = { goWelcome() })
                    } else if (providers.isEmpty() && authSession != null) {
                        OnboardingPage(title = "Tu cuenta está lista", subtitle = "Ya tienes una sesión de Pokke activa. Puedes continuar con ella.", page = 4, onBack = { step = 2 }, nextLabel = "Continuar", onNext = { goWelcome() }) {
                            Text("También puedes administrar tu cuenta desde tu perfil.", color = Color.White.copy(.65f), textAlign = TextAlign.Center)
                        }
                    } else if (providerIndex >= providers.sortedWith(providerOrder()).size) {
                        OnboardingPage(title = "¡Todo listo!", subtitle = "Tus servicios seleccionados ya fueron procesados.", page = 4, onBack = { providerIndex = (providers.size - 1).coerceAtLeast(0) }, nextLabel = "Continuar", onNext = { goWelcome() }) {
                            Text("Puedes administrar tus conexiones desde Ajustes · Cuentas externas.", color = Color.White.copy(.65f), textAlign = TextAlign.Center)
                        }
                    } else {
                        val provider = providers.sortedWith(providerOrder())[providerIndex]
                        ExternalProviderStep(provider = provider, store = externalStore, onConnected = {
                            providerIndex++
                        }, onSkip = { providerIndex++ })
                    }
                }
                else -> WelcomeScreen(nickname = nickname.ifBlank { "amigo" })
            }
        }
    }
}

private fun providerOrder(): Comparator<String> = compareBy { when (it) { "spotify" -> 0; "ytmusic" -> 1; else -> 2 } }
private fun providerLabel(provider: String): String = when (provider) {
    "spotify" -> "Spotify"
    "ytmusic" -> "YouTube Music"
    else -> "Discord"
}

@Composable
private fun OnboardingPage(
    title: String,
    subtitle: String,
    page: Int,
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit,
    nextLabel: String = "Siguiente",
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("POKKE", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(4) { i -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(if (i < page) Violet else Color.White.copy(.13f))) } }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(.58f), fontSize = 14.sp, lineHeight = 20.sp)
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color.White.copy(.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Atrás") }
            Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(if (onBack == null) 1f else 1.5f).height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF11101A))) { Text(nextLabel, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ProviderCard(id: String, name: String, description: String, symbol: String, selected: Set<String>, accent: Color, onToggle: (String) -> Unit) {
    val active = id in selected
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (active) accent.copy(.15f) else Color.White.copy(.055f)).clickable { onToggle(id) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(.2f)), contentAlignment = Alignment.Center) { Text(symbol, color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color.White.copy(.56f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Checkbox(active, { onToggle(id) }, colors = CheckboxDefaults.colors(checkedColor = accent))
    }
}

@Composable
private fun ExternalProviderStep(provider: String, store: ExternalAuthStore, onConnected: () -> Unit, onSkip: () -> Unit) {
    BackHandler { onSkip() }
    Box(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Conectar ${providerLabel(provider)}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Inicia sesión con el servicio que elegiste. Pokke no te pedirá la contraseña fuera de su página de acceso.", color = Color.White.copy(.6f), textAlign = TextAlign.Center, fontSize = 12.sp)
        }
        when (provider) {
            "spotify" -> SpotifyLoginScreen(onConnected = { token -> store.upsert(ExternalSession("spotify", "Spotify", "sp_dc=$token")); onConnected() })
            "ytmusic" -> YouTubeMusicConnectScreen(store = store, onConnected = onConnected)
            else -> DiscordOAuthPending(onContinue = onConnected)
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { Text("Ahora no", color = Color.White.copy(.75f)) }
    }
}

@Composable
private fun WelcomeScreen(nickname: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else .78f, spring(dampingRatio = .55f, stiffness = 180f), label = "welcome-scale")
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(132.dp).scale(scale).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Pink))), contentAlignment = Alignment.Center) {
            Text("♪", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(34.dp))
        Text("¡Bienvenido, $nickname!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Gracias por unirte a Pokke.\nEsperamos que disfrutes tu música.", color = Color.White.copy(.68f), fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(42.dp))
        CircularProgressIndicator(Modifier.size(22.dp), color = Violet, strokeWidth = 2.dp)
    }
}

@Composable
private fun onboardingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = Violet, unfocusedBorderColor = Color.White.copy(.2f),
    focusedLabelColor = Violet, unfocusedLabelColor = Color.White.copy(.5f),
    cursorColor = Violet
)


@Composable
private fun YouTubeMusicConnectScreen(store: ExternalAuthStore, onConnected: () -> Unit) {
    var pageReady by remember { mutableStateOf(false) }
    var captureRequest by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        YtMusicLoginScreen(
            mode = com.pokke.music.auth.external.WebSessionMode.SIGN_IN,
            modifier = Modifier.fillMaxSize(),
            captureRequest = captureRequest,
            onPageReady = { pageReady = it },
            onCaptured = { captured ->
                store.upsert(ExternalSession("ytmusic", "YouTube Music", captured.cookie))
                onConnected()
            }
        )
        if (pageReady) {
            Button(
                onClick = { captureRequest++ },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF11101A))
            ) { Text("Usar esta cuenta", fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Discord OAuth via polic.onrender.com:
 * 1) App opens GET https://polic.onrender.com/api/auth/discord
 * 2) Server redirects to Discord, then handles /api/auth/discord/callback
 * 3) Server should deep-link back to pokke://discord/callback?status=success
 * 4) MainActivity.handleDiscordCallback marks discordPolicLinked = true
 */
@Composable
private fun DiscordOAuthPending(onContinue: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferences(context.applicationContext) }
    val linked by prefs.discordPolicLinked.collectAsState(initial = false)
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(linked) {
        if (linked) onContinue()
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Conectar Discord",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Te llevamos a Discord para autorizar Pokke. Al terminar, vuelve a la app automáticamente.",
            color = Color.White.copy(.65f),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                opened = true
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://polic.onrender.com/api/auth/discord")
                )
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF11101A))
        ) {
            Text(if (opened) "Abrir Discord de nuevo" else "Conectar con Discord", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onContinue) {
            Text("Continuar sin Discord", color = Color.White.copy(.7f))
        }
        if (opened && !linked) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Esperando autorización… Si ya autorizaste, vuelve a Pokke desde Discord.",
                color = Color.White.copy(.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
