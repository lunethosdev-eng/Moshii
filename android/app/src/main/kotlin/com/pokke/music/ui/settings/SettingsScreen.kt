package com.pokke.music.ui.settings

import com.pokke.music.player.PlayerController
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.ui.settings.ExternalAccountsSection
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.pairing.DeviceLinkRepository
import kotlinx.coroutines.launch
import com.pokke.music.ui.liquid.LocalAppBackdrop
import com.pokke.music.ui.liquid.LiquidToggle
import com.pokke.music.ui.liquid.LiquidGlassPanel
import com.pokke.music.ui.motion.MotionSettings
import android.net.Uri
import com.pokke.music.ui.plugins.PluginStoreSection

private const val OWNER_EMAIL = "lunethos.dev@gmail.com"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null,
    authRepository: AuthRepository? = null,
    pluginDeepLinkUri: Uri? = null,
    onPluginDeepLinkHandled: () -> Unit = {},
    onRestartOnboarding: () -> Unit = {},
    onOpenRemoteControl: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferences(context) }
    val scope = rememberCoroutineScope()
    val glass by prefs.liquidGlassEnabled.collectAsState(initial = false)
    val accent by prefs.accentColor.collectAsState(initial = "white")
    val theme by prefs.appTheme.collectAsState(initial = "dark")
    val dynamicColors by prefs.dynamicColors.collectAsState(initial = true)
    val gapless by prefs.gaplessPlayback.collectAsState(initial = true)
    val showListening by prefs.showListeningOnProfile.collectAsState(initial = true)
    val localCanvas by prefs.pokkeAnimatedCoversEnabled.collectAsState(initial = true)
    val externalCanvas by prefs.externalCanvasEnabled.collectAsState(initial = true)
    val spotifyCanvas by prefs.spotifyCanvasEnabled.collectAsState(initial = false)
    val canvasRepeat by prefs.canvasRepeatEnabled.collectAsState(initial = true)
    val performance by prefs.liquidPerformanceMode.collectAsState(initial = true)
    val chatsEnabled by prefs.experimentalChatsEnabled.collectAsState(initial = false)
    val mapsEnabled by prefs.experimentalMapsEnabled.collectAsState(initial = false)
    val motion by prefs.motionSettings.collectAsState(initial = MotionSettings())

    val session by (authRepository?.session ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val isOwner = session?.email?.equals(OWNER_EMAIL, ignoreCase = true) == true
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categoryTitle = when (selectedCategory) {
        "appearance" -> "Apariencia y movimiento"
        "home" -> "Personalizar inicio"
        "search" -> "Búsqueda y letras"
        "accounts" -> "Cuentas y tema"
        "privacy" -> "Privacidad y perfil"
        "devices" -> "Dispositivos vinculados"
        "playback" -> "Reproducción"
        "plugins" -> "Tienda de plugins"
        "experimental" -> "Funciones experimentales"
        else -> "Ajustes"
    }


    Column(
        Modifier
            .fillMaxSize()
            .animateContentSize()
            .background(if (glass) Color(0xE609090D) else Color(0xFF09090B))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selectedCategory == null) onBack() else selectedCategory = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column {
                Text(categoryTitle, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedCategory == null) "Todo lo que puedes personalizar en Pokke"
                    else "Los cambios se aplican al instante y conservan tus preferencias",
                    color = Color.White.copy(.5f),
                    fontSize = 12.sp
                )
            }
        }

        if (selectedCategory == null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRestartOnboarding, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Editar preferencias iniciales")
                }
                SettingsCategoryCard("Apariencia y movimiento", "Liquid Glass, Canvas, Motion Engine y tema visual", "appearance") { selectedCategory = it }
                SettingsCategoryCard("Personalizar inicio", "Secciones, distribución, carátulas y fondo de Home", "home") { selectedCategory = it }
                SettingsCategoryCard("Búsqueda y letras", "Catálogos activos, historial y estilo de letras", "search") { selectedCategory = it }
                SettingsCategoryCard("Cuentas externas y tema", "Conexiones externas, tema y colores", "accounts") { selectedCategory = it }
                SettingsCategoryCard("Privacidad y perfil", "Visibilidad de tu actividad", "privacy") { selectedCategory = it }
                SettingsCategoryCard("Dispositivos vinculados", "Ver y cerrar sesiones de tus teléfonos y televisores", "devices") { selectedCategory = it }
                SettingsCategoryCard("Reproducción", "Opciones de reproducción y gapless", "playback") { selectedCategory = it }
                SettingsCategoryCard("Tienda de plugins", "Extiende Pokke con módulos compatibles", "plugins") { selectedCategory = it }
                SettingsCategoryCard("Funciones experimentales", "Funciones alfa y controles opcionales", "experimental") { selectedCategory = it }
                if (isOwner && onOpenAdmin != null) {
                    SettingsCategoryCard("Administración", "Herramientas de propietario", "experimental") { selectedCategory = it }
                }
            }
        } else {
        if (selectedCategory == "appearance") {
        Section("APARIENCIA")
        LiquidOrMaterialToggle(
            title = "Liquid Glass",
            subtitle = "Vidrio dinámico con desenfoque, refracción, reflejos y respuesta táctil. Usa una captura compartida del contenido.",
            checked = glass,
            glass = glass,
            onChange = { scope.launch { prefs.setLiquidGlass(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Canvas local de artistas",
            subtitle = "Reproduce clips incluidos en la app cuando la canción coincide con un artista compatible. No consume datos móviles.",
            checked = localCanvas,
            glass = glass,
            onChange = { scope.launch { prefs.setPokkeAnimatedCoversEnabled(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Canvas externos",
            subtitle = "Busca motion artwork en Apple Music, Tidal y el catálogo comunitario. Requiere conexión; Spotify necesita vinculación de sesión y aún no se activa aquí.",
            checked = externalCanvas,
            glass = glass,
            onChange = { scope.launch { prefs.setExternalCanvasEnabled(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Spotify Canvas",
            subtitle = "Usa la sesión de Spotify que conectaste en Cuentas externas. Depende de endpoints privados de Spotify y puede dejar de funcionar si cambian.",
            checked = spotifyCanvas,
            glass = glass,
            onChange = { scope.launch { prefs.setSpotifyCanvasEnabled(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Repetir Canvas durante la canción",
            subtitle = "Mantiene el Canvas en bucle mientras se reproduce la canción. Si lo desactivas, al terminar el clip vuelve la portada.",
            checked = canvasRepeat,
            glass = glass,
            onChange = { scope.launch { prefs.setCanvasRepeatEnabled(it) } }
        )

        if (glass) {
            LiquidOrMaterialToggle(
                title = "Modo rendimiento",
                subtitle = "Prioriza efectos solo en la pantalla visible y reduce capas y trabajo gráfico fuera de ella.",
                checked = performance,
                glass = glass,
                onChange = { scope.launch { prefs.setLiquidPerformanceMode(it) } }
            )
            Text("El modo rendimiento evita mantener capturas y efectos de cristal en pantallas que no están visibles.", color = Color.White.copy(.42f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        }


        Section("TORI MOTION ENGINE")
        Text(
            "Motor global de movimiento de Tori. Controla transiciones, respuesta táctil y profundidad en las superficies compatibles. Respeta la preferencia del sistema para reducir movimiento.",
            color = Color.White.copy(.5f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 5.dp)
        )
        LiquidOrMaterialToggle(
            title = "Activar Motion Engine",
            subtitle = "Control maestro para las animaciones compatibles.",
            checked = motion.enabled,
            glass = glass,
            onChange = { scope.launch { prefs.setMotionEnabled(it) } }
        )
        Text("Perfil de movimiento", color = Color.White.copy(.75f), fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                MotionSettings.PROFILE_FULL to "Completo",
                MotionSettings.PROFILE_BALANCED to "Equilibrado",
                MotionSettings.PROFILE_LIGHT to "Ligero"
            ).forEach { (id, label) ->
                val selected = motion.profile == id
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFFC3C44) else Color(0xFF202024))
                        .clickable { scope.launch { prefs.setMotionProfile(id) } }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (selected) Color.Black else Color.White, fontSize = 11.sp) }
            }
        }
        Text("Intensidad", color = Color.White.copy(.75f), fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp))
        Slider(
            value = motion.intensity,
            onValueChange = { v -> scope.launch { prefs.setMotionIntensity(v) } },
            valueRange = 0f..1.8f,
            modifier = Modifier.padding(horizontal = 22.dp),
            enabled = motion.enabled
        )
        Text("Velocidad", color = Color.White.copy(.75f), fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, top = 4.dp))
        Slider(
            value = motion.speed,
            onValueChange = { v -> scope.launch { prefs.setMotionSpeed(v) } },
            valueRange = 0.5f..1.8f,
            modifier = Modifier.padding(horizontal = 22.dp),
            enabled = motion.enabled
        )
        Text("Profundidad 3D", color = Color.White.copy(.75f), fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, top = 4.dp))
        Slider(
            value = motion.depth,
            onValueChange = { v -> scope.launch { prefs.setMotionDepth(v) } },
            valueRange = 0f..1f,
            modifier = Modifier.padding(horizontal = 22.dp),
            enabled = motion.enabled
        )
        LiquidOrMaterialToggle(
            title = "Iluminación y reflejos",
            subtitle = "Permite que los componentes compatibles utilicen reflejos ambientales.",
            checked = motion.reflections,
            glass = glass,
            onChange = { scope.launch { prefs.setMotionReflections(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Parallax",
            subtitle = "Movimiento de profundidad en superficies compatibles.",
            checked = motion.parallax,
            glass = glass,
            onChange = { scope.launch { prefs.setMotionParallax(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Respuesta táctil",
            subtitle = "Habilita la opción de feedback háptico para componentes compatibles.",
            checked = motion.haptics,
            glass = glass,
            onChange = { scope.launch { prefs.setMotionHaptics(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Reducir movimiento",
            subtitle = "Desactiva las animaciones del motor que no sean necesarias para la interacción.",
            checked = motion.reduceMotion,
            glass = glass,
            onChange = { scope.launch { prefs.setMotionReduce(it) } }
        )

        }
        if (selectedCategory == "plugins") {
        Section("TIENDA DE PLUGINS")
        PluginStoreSection(
            deepLinkUri = pluginDeepLinkUri,
            onDeepLinkHandled = onPluginDeepLinkHandled
        )

        }
        if (selectedCategory == "home") {
        Section("PERSONALIZAR INICIO")
        Text(
            "Controla qué secciones aparecen en la pantalla principal y su densidad. Los cambios se guardan y se aplican al volver a Inicio.",
            color = Color.White.copy(.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
        )
        val showNowPlaying by prefs.homeShowNowPlaying.collectAsState(initial = true)
        val showQuick by prefs.homeShowQuickAccess.collectAsState(initial = true)
        val showRecent by prefs.homeShowRecent.collectAsState(initial = true)
        val showReco by prefs.homeShowRecommendations.collectAsState(initial = true)
        val showLib by prefs.homeShowLibraryShortcuts.collectAsState(initial = true)
        val homeLayout by prefs.homeLayout.collectAsState(initial = "comfortable")
        val homeDensity by prefs.homeDensity.collectAsState(initial = "medium")
        val homeSectionOrder by prefs.homeSectionOrder.collectAsState(initial = "now_playing,quick_access,recent,recommendations,library")
        val homeOrderItems = homeSectionOrder.split(",").filter { it in setOf("now_playing", "quick_access", "recent", "recommendations", "library") }.distinct()
        val homeOrderLabels = mapOf(
            "now_playing" to "Reproduciendo",
            "quick_access" to "Accesos rápidos",
            "recent" to "Recientes",
            "recommendations" to "Para ti",
            "library" to "Biblioteca"
        )

        LiquidOrMaterialToggle(
            title = "Mostrar «Reproduciendo»",
            subtitle = "Tarjeta de la canción actual en el Home.",
            checked = showNowPlaying,
            glass = glass,
            onChange = { scope.launch { prefs.setHomeShowNowPlaying(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Accesos rápidos",
            subtitle = "Chips de aleatorio, favoritos, biblioteca y búsqueda.",
            checked = showQuick,
            glass = glass,
            onChange = { scope.launch { prefs.setHomeShowQuickAccess(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Recientes",
            subtitle = "Fila horizontal de canciones recientes.",
            checked = showRecent,
            glass = glass,
            onChange = { scope.launch { prefs.setHomeShowRecent(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Para ti / recomendaciones",
            subtitle = "Lista de sugerencias basada en recientes.",
            checked = showReco,
            glass = glass,
            onChange = { scope.launch { prefs.setHomeShowRecommendations(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Atajos de biblioteca",
            subtitle = "Accesos a me gusta, playlists y recientes.",
            checked = showLib,
            glass = glass,
            onChange = { scope.launch { prefs.setHomeShowLibraryShortcuts(it) } }
        )

        // Layout density
        Text("Distribución del Home", color = Color.White.copy(.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("compact" to "Compacta", "comfortable" to "Cómoda", "dense" to "Densa").forEach { (id, label) ->
                val selected = homeLayout == id
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color.White else Color.White.copy(0.12f))
                        .clickable { scope.launch { prefs.setHomeLayout(id) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Text("Tamaño de carátulas", color = Color.White.copy(.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("small" to "Pequeño", "medium" to "Medio", "large" to "Grande").forEach { (id, label) ->
                val selected = homeDensity == id
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color.White else Color.White.copy(0.12f))
                        .clickable { scope.launch { prefs.setHomeDensity(id) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        TextButton(
            onClick = { scope.launch { prefs.resetHomeCustomization() } },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Restaurar distribución predeterminada")
        }
        Text("Orden de las secciones", color = Color.White.copy(.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp))
        homeOrderItems.forEachIndexed { index, sectionId ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f))
                    .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(homeOrderLabels[sectionId] ?: sectionId, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = index > 0, onClick = {
                    val reordered = homeOrderItems.toMutableList()
                    val moved = reordered.removeAt(index)
                    reordered.add(index - 1, moved)
                    scope.launch { prefs.setHomeSectionOrder(reordered.joinToString(",")) }
                }) { Text("↑", color = Color.White) }
                TextButton(enabled = index < homeOrderItems.lastIndex, onClick = {
                    val reordered = homeOrderItems.toMutableList()
                    val moved = reordered.removeAt(index)
                    reordered.add(index + 1, moved)
                    scope.launch { prefs.setHomeSectionOrder(reordered.joinToString(",")) }
                }) { Text("↓", color = Color.White) }
            }
        }

        }
        if (selectedCategory == "search") {
        Section("BÚSQUEDA Y CATÁLOGOS")
        val ytCatalog by prefs.youtubeCatalogEnabled.collectAsState(initial = false)
        val searchHistory by prefs.searchHistory.collectAsState(initial = emptyList())
        LiquidOrMaterialToggle(
            title = "Catálogo YouTube Music / InnerTube",
            subtitle = "Cuando está activo, la búsqueda combina el catálogo de Tori con YouTube Music. Cuando está apagado, solo consulta las fuentes habilitadas de Tori/Ukiyo.",
            checked = ytCatalog,
            glass = glass,
            onChange = { scope.launch { prefs.setYoutubeCatalogEnabled(it) } }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Historial de búsqueda", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${searchHistory.size} búsquedas recientes guardadas en este dispositivo", color = Color.White.copy(.48f), fontSize = 11.sp)
            }
            TextButton(onClick = { scope.launch { prefs.clearSearchHistory() } }, enabled = searchHistory.isNotEmpty()) { Text("Borrar", color = Color(0xFFFC3C44)) }
        }
        Text("El historial es local y no se comparte con otros dispositivos. Las preferencias visuales y la distribución de Home sí se sincronizan con tu cuenta.", color = Color.White.copy(.4f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))


        
        // —— Letras ——
        Text(
            "Letras",
            color = Color.White.copy(.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp)
        )
        val colorMode by prefs.lyricsColorMode.collectAsState("classic")
        val anim by prefs.lyricsAnimIntensity.collectAsState(1f)
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("classic" to "Clásico", "emotion" to "Emoción", "mono" to "Mono").forEach { (id, label) ->
                val sel = colorMode == id
                Text(
                    label,
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Color.White else Color(0xFF2A2A32))
                        .clickable { scope.launch { prefs.setLyricsColorMode(id) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Text(
            "Intensidad animación letras",
            color = Color.White.copy(.75f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, top = 10.dp)
        )
        Slider(
            value = anim,
            onValueChange = { v -> scope.launch { prefs.setLyricsAnimIntensity(v) } },
            valueRange = 0.3f..1.8f,
            modifier = Modifier.padding(horizontal = 22.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )

        }
        if (selectedCategory == "home") {
        // —— Fondo Home ——
        Text(
            "Fondo custom Home (URL)",
            color = Color.White.copy(.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp)
        )
        val bg by prefs.homeCustomBgUrl.collectAsState("")
        var bgField by remember(bg) { mutableStateOf(bg) }
        androidx.compose.material3.OutlinedTextField(
            value = bgField,
            onValueChange = { bgField = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            singleLine = true,
            placeholder = { Text("https://…") }
        )
        Text(
            "Guardar fondo",
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 22.dp, top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable { scope.launch { prefs.setHomeCustomBgUrl(bgField.trim()) } }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )


        }
        if (selectedCategory == "accounts") {
        Section("CUENTAS EXTERNAS")
        ExternalAccountsSection(prefs, authRepository)

        Text(
            "Tema",
            color = Color.White.copy(.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 8.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Oscuro", theme == "dark") { scope.launch { prefs.setAppTheme("dark") } }
            ChoiceChip("Claro", theme == "light") { scope.launch { prefs.setAppTheme("light") } }
            ChoiceChip("Sistema", theme == "system") { scope.launch { prefs.setAppTheme("system") } }
        }

        Text(
            "Color de acento",
            color = Color.White.copy(.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "white" to Color.White,
                "violet" to Color(0xFFB69CFF),
                "blue" to Color(0xFF83B9FF),
                "pink" to Color(0xFFFFA6D2),
                "green" to Color(0xFF9DDBB2)
            ).forEach { (id, color) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(color, RoundedCornerShape(12.dp))
                        .clickable { scope.launch { prefs.setAccentColor(id) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (accent == id) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        LiquidOrMaterialToggle(
            title = "Colores dinámicos del sistema",
            subtitle = "Usa la paleta de Android 12 o superior. Desactívalo para respetar el color de acento elegido arriba.",
            checked = dynamicColors,
            glass = glass,
            onChange = { scope.launch { prefs.setDynamicColors(it) } }
        )

        }
        if (selectedCategory == "privacy") {
        Section("PRIVACIDAD Y PERFIL")
        LiquidOrMaterialToggle(
            title = "Mostrar lo que escucho",
            subtitle = "Otros pueden ver la canción actual en tu perfil público.",
            checked = showListening,
            glass = glass,
            onChange = { scope.launch { prefs.setShowListeningOnProfile(it) } }
        )

        }
        if (selectedCategory == "devices") {
            Section("CONTROL REMOTO DE POKKE TV")
            OutlinedButton(onClick = onOpenRemoteControl, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Abrir control remoto") }
            Text("Busca televisores Pokke en la misma red Wi‑Fi. La TV muestra un código temporal para autorizar el control local.", color = Color.White.copy(.55f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
            Section("TUS DISPOSITIVOS")
            val deviceRepo = remember(authRepository) { DeviceLinkRepository(authRepository) }
            var devices by remember { mutableStateOf<List<DeviceLinkRepository.LinkedDevice>>(emptyList()) }
            var deviceError by remember { mutableStateOf<String?>(null) }
            var loadingDevices by remember { mutableStateOf(true) }
            var securityEvents by remember { mutableStateOf<List<DeviceLinkRepository.SecurityEvent>>(emptyList()) }
            var blockedDevices by remember { mutableStateOf<List<DeviceLinkRepository.LinkedDevice>>(emptyList()) }
            var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }
            LaunchedEffect(deviceRepo) {
                deviceRepo.linkedDevices().onSuccess { devices = it }.onFailure { deviceError = it.message }
                deviceRepo.securityHistory().onSuccess { securityEvents = it }
                deviceRepo.blockedDevices().onSuccess { blockedDevices = it }
                loadingDevices = false
            }
            Text("El teléfono principal es el propietario de la cuenta. Ningún dispositivo secundario puede cerrar su sesión.", color = Color.White.copy(.58f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
            if (loadingDevices) CircularProgressIndicator(modifier = Modifier.padding(22.dp))
            deviceError?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(22.dp)) }
            if (!loadingDevices && devices.isEmpty() && deviceError == null) Text("Todavía no hay dispositivos vinculados. Vincula tu TV desde su pantalla de perfil.", color = Color.White.copy(.6f), modifier = Modifier.padding(22.dp))
            devices.forEach { device ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).background(Color(0xFF17171E), RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(if (device.isPrimary) "Teléfono principal · Propietario" else if (device.kind == "android_tv") "Android TV" else "Teléfono secundario", color = Color.White.copy(.55f), fontSize = 12.sp)
                        }
                        if (device.isPrimary) Text("PROTEGIDO", color = Color(0xFF9BE7B0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!device.isPrimary) {
                        HorizontalDivider(color = Color.White.copy(.08f), modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Dispositivo de confianza", color = Color.White, fontSize = 13.sp)
                                Text("Marcar este dispositivo como reconocido", color = Color.White.copy(.48f), fontSize = 11.sp)
                            }
                            Switch(checked = device.trusted, onCheckedChange = { enabled ->
                                scope.launch { deviceRepo.updateDevicePermissions(device.id, enabled, device.canCloseOtherSessions).onSuccess { devices = devices.map { if (it.id == device.id) it.copy(trusted = enabled) else it } }.onFailure { deviceError = it.message } }
                            })
                        }
                        if (device.kind == "android_tv") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Puede cerrar sesiones secundarias", color = Color.White, fontSize = 13.sp)
                                    Text("Nunca puede cerrar la sesión del teléfono principal", color = Color.White.copy(.48f), fontSize = 11.sp)
                                }
                                Switch(checked = device.canCloseOtherSessions, onCheckedChange = { enabled ->
                                    scope.launch { deviceRepo.updateDevicePermissions(device.id, device.trusted, enabled).onSuccess { devices = devices.map { if (it.id == device.id) it.copy(canCloseOtherSessions = enabled) else it } }.onFailure { deviceError = it.message } }
                                })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { pendingAction = device.id to "block" }) { Text("Bloquear", color = Color(0xFFFF7777)) }
                            TextButton(onClick = { pendingAction = device.id to "revoke" }) { Text("Desvincular", color = Color(0xFFFFB0B0)) }
                        }
                    }
                }
            }
            Section("LISTA DE DISPOSITIVOS BLOQUEADOS")
            if (blockedDevices.isEmpty()) Text("La blacklist está vacía.", color = Color.White.copy(.5f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            blockedDevices.forEach { blocked ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).background(Color(0xFF211719), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(blocked.name, color = Color.White, fontSize = 13.sp)
                        Text("Bloqueado · ${blocked.kind}", color = Color(0xFFFF9A9A), fontSize = 11.sp)
                    }
                    TextButton(onClick = { scope.launch {
                        deviceRepo.unblockDevice(blocked.id).onSuccess { blockedDevices = blockedDevices.filterNot { it.id == blocked.id }; deviceRepo.securityHistory().onSuccess { securityEvents = it } }
                            .onFailure { deviceError = it.message }
                    } }) { Text("Quitar bloqueo") }
                }
            }
            if (pendingAction != null) {
                val target = devices.firstOrNull { it.id == pendingAction!!.first }
                val isBlock = pendingAction!!.second == "block"
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text(if (isBlock) "Bloquear dispositivo" else "Cerrar sesión y desvincular") },
                    text = { Text("¿Confirmas esta acción para ${target?.name ?: "este dispositivo"}? El teléfono principal está protegido y no puede modificarse desde aquí.") },
                    confirmButton = { TextButton(onClick = {
                        val action = pendingAction!!; pendingAction = null
                        scope.launch {
                            val result = if (isBlock) deviceRepo.blockDevice(action.first) else deviceRepo.revokeDevice(action.first)
                            result.onSuccess { devices = devices.filterNot { it.id == action.first }; if (isBlock) deviceRepo.blockedDevices().onSuccess { blockedDevices = it }; deviceRepo.securityHistory().onSuccess { securityEvents = it } }
                                .onFailure { deviceError = it.message }
                        }
                    }) { Text(if (isBlock) "Bloquear" else "Desvincular") } },
                    dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancelar") } }
                )
            }
            Section("HISTORIAL DE SEGURIDAD")
            if (securityEvents.isEmpty()) Text("No hay actividad de seguridad registrada todavía.", color = Color.White.copy(.5f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            securityEvents.forEach { event ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                    Text(event.eventType.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${event.deviceName} · ${event.details}", color = Color.White.copy(.52f), fontSize = 11.sp)
                    Text(event.createdAt, color = Color.White.copy(.35f), fontSize = 10.sp)
                }
            }
        }

        if (selectedCategory == "playback") {
        Section("REPRODUCCIÓN")
        LiquidOrMaterialToggle(
            title = "Reproducción gapless",
            subtitle = "Intenta encadenar pistas sin silencio.",
            checked = gapless,
            glass = glass,
            onChange = { scope.launch { prefs.setGaplessPlayback(it); PlayerController.setGapless(it) } }
        )

        }
        if (selectedCategory == "experimental") {
        Section("FUNCIONES EXPERIMENTALES")
        Text("Funciones alfa: pueden cambiar y requieren la migración SQL correspondiente en Supabase.", color = Color.White.copy(.48f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        LiquidOrMaterialToggle(
            title = "Chats · Alfa 1.0",
            subtitle = "Conversaciones entre amigos aceptados. Activa el acceso experimental.",
            checked = chatsEnabled,
            glass = glass,
            onChange = { scope.launch { prefs.setExperimentalChats(it) } }
        )
        LiquidOrMaterialToggle(
            title = "Mapas con amigos · Alfa 1.0",
            subtitle = "Requiere Chats. La ubicación se comparte solo después de conceder permiso y elegir amigos.",
            checked = mapsEnabled,
            glass = glass,
            onChange = { if (chatsEnabled) scope.launch { prefs.setExperimentalMaps(it) } },
        )
        if (!chatsEnabled && mapsEnabled) {
            Text("Activa Chats antes de habilitar Mapas.", color = Color(0xFFE5C17A), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp))
        }

        if (isOwner && onOpenAdmin != null) {
            Section("OWNER")
            Button(
                onClick = onOpenAdmin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFC3C44),
                    contentColor = Color.Black
                )
            ) {
                Text("Admin Panel (Owner)", fontWeight = FontWeight.Bold)
            }
        }

        }
        Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SettingsCategoryCard(title: String, subtitle: String, category: String, onClick: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        enabled = true,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = null) { onClick(category) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFC3C44)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(.52f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                }
            }
            Text("›", color = Color(0xFFFC3C44), fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun LiquidOrMaterialToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    glass: Boolean,
    onChange: (Boolean) -> Unit
) {
    val appBackdrop = LocalAppBackdrop.current
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        enabled = true,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clickable { onChange(!checked) }) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    color = Color.White.copy(.52f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (glass && appBackdrop != null) {
                LiquidToggle(selected = { checked }, onSelect = onChange, backdrop = appBackdrop, modifier = Modifier.size(width = 54.dp, height = 34.dp))
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFC3C44),
                        uncheckedThumbColor = Color(0xFFB8B8C0),
                        uncheckedTrackColor = Color(0xFF333338)
                    )
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        color = Color(0xFFFC3C44).copy(alpha = 0.9f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun RowScope.ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .background(if (selected) Color.White else Color(0xFF202024), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp)
    }
}
