package com.pokke.music.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.MusicRepository
import com.pokke.music.util.ShareLinks
import com.pokke.music.ui.library.PublicPlaylistScreen
import com.pokke.music.player.PlayerController
import com.pokke.music.ui.auth.LoginScreen
import com.pokke.music.ui.auth.ProfileScreen
import com.pokke.music.ui.home.HomeScreen
import com.pokke.music.ui.library.LibraryScreen
import com.pokke.music.ui.player.BitChordNowPlayingHost
import com.pokke.music.ui.player.PlayerScreenPersistenceGate
import com.pokke.music.ui.player.LyricsScreen
import com.pokke.music.ui.queue.QueueScreen
import com.pokke.music.ui.artist.ArtistScreen
import com.pokke.music.ui.album.AlbumScreen
import com.pokke.music.ui.search.SearchScreen
import com.pokke.music.ui.settings.SettingsScreen
import com.pokke.music.ui.admin.AdminPanelScreen
import com.pokke.music.ui.social.ChatScreen
import com.pokke.music.ui.player.MiniPlayerBar
import com.pokke.music.ui.player.PlayerDock
import com.pokke.music.ui.player.PlayerMotionSheet
import com.pokke.music.ui.player.PlayerSheetMotion
import com.pokke.music.ui.player.LocalMiniPlayerPull
import com.pokke.music.ui.player.LocalPlayerDock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import com.pokke.music.ui.social.StoryPublisherScreen
import com.pokke.music.ui.social.FriendsMapScreen
import com.pokke.music.ui.liquid.LocalAppBackdrop
import com.pokke.music.ui.liquid.pokkePressMotion
import com.pokke.music.ui.motion.MotionSettings
import com.pokke.music.ui.motion.ToriMotionEngine
import com.pokke.music.ui.liquid.LocalGlassPerformanceMode
import com.pokke.music.ui.backdrop.drawBackdrop
import com.pokke.music.ui.backdrop.effects.blur
import com.pokke.music.ui.backdrop.effects.lens
import com.pokke.music.ui.backdrop.effects.vibrancy
import com.pokke.music.ui.shapes.Capsule
import com.pokke.music.data.remote.UpdateChecker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.Intent
import android.net.Uri
import com.pokke.music.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Profile : Screen("profile")
    data object PublicProfile : Screen("user/{username}") {
        fun create(username: String) = "user/${android.net.Uri.encode(username.trim().lowercase().removePrefix("@"))}"
    }
    data object PublicPlaylist : Screen("shared_playlist/{playlistId}") {
        fun create(id: String) = "shared_playlist/${android.net.Uri.encode(id)}"
    }
    data object SharedSong : Screen("shared_song/{songId}") {
        fun create(id: String) = "shared_song/${android.net.Uri.encode(id)}"
    }
    data object SharedLyrics : Screen("shared_lyrics/{songId}") {
        fun create(id: String) = "shared_lyrics/${android.net.Uri.encode(id)}"
    }
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Chats : Screen("chats")
    data object Player : Screen("player")
    data object Queue : Screen("queue")
    data object Lyrics : Screen("lyrics")
    data object Settings : Screen("settings")
    data object RemoteControl : Screen("remote_control")
    data object Admin : Screen("admin")
    data object StoryPublish : Screen("story_publish")
    data object Maps : Screen("maps")
    data object Artist : Screen("artist/{name}") {
        fun create(name: String) = "artist/${android.net.Uri.encode(name.trim())}"
    }
    data object Album : Screen("album/{name}?artist={artist}") {
        fun create(name: String, artist: String? = null) =
            "album/${android.net.Uri.encode(name.trim())}?artist=${android.net.Uri.encode(artist.orEmpty())}"
    }
}

private data class NavItem(val screen: Screen, val selected: ImageVector, val normal: ImageVector)
private val baseNavItems = listOf(
    NavItem(Screen.Home, Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(Screen.Search, Icons.Filled.Search, Icons.Outlined.Search)
)

@Composable
fun PokkeNavHost(pluginDeepLinkUri: Uri? = null, onPluginDeepLinkHandled: () -> Unit = {}, onRestartOnboarding: () -> Unit = {}) {
    val nav = rememberNavController()
    val context = LocalContext.current
    LaunchedEffect(pluginDeepLinkUri) {
        val target = ShareLinks.parse(pluginDeepLinkUri) ?: return@LaunchedEffect
        when (target) {
            is ShareLinks.Target.Profile -> nav.navigate(Screen.PublicProfile.create(target.username))
            is ShareLinks.Target.Song -> nav.navigate(Screen.SharedSong.create(target.id))
            is ShareLinks.Target.Playlist -> nav.navigate(Screen.PublicPlaylist.create(target.id))
            is ShareLinks.Target.Lyrics -> nav.navigate(Screen.SharedLyrics.create(target.id))
            is ShareLinks.Target.Box -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, ShareLinks.webForTarget(target)))
            }
        }
        onPluginDeepLinkHandled()
    }
    val prefs = remember { UserPreferences(context) }
    val auth = remember(context) { AuthRepository(context) }
    val authSession by auth.session.collectAsState()

    LaunchedEffect(pluginDeepLinkUri) {
        if (pluginDeepLinkUri?.scheme == "pokke" && pluginDeepLinkUri.host == "plugin") {
            nav.navigate(Screen.Settings.route) { launchSingleTop = true }
        }
    }

    var updateInfo by remember { mutableStateOf<com.pokke.music.data.remote.AppRelease?>(null) }
    LaunchedEffect(Unit) {
        UpdateChecker.fetchLatest().onSuccess { rel ->
            if (UpdateChecker.isNewer(rel.tag, BuildConfig.VERSION_NAME)) updateInfo = rel
        }
    }
    updateInfo?.let { rel ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Nueva versión ${rel.tag}") },
            text = {
                Text((rel.name + "\n\n" + rel.body).take(900).ifBlank { "Hay una actualización disponible." })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (rel.htmlUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.htmlUrl)))
                    }
                    updateInfo = null
                }) { Text("Ver release") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Después") }
            }
        )
    }

    LaunchedEffect(auth) { auth.restoreSession() }

    // Pull first, then reconcile account changes periodically while the app is open.
    // This keeps Home and theme choices aligned across devices without touching player settings.
    LaunchedEffect(authSession?.userId) {
        if (authSession != null) {
            val initialRemote = auth.getAccountAppSettings()
            if (initialRemote.isSuccess) {
                val remoteSettings = initialRemote.getOrNull()
                var initialUploadSucceeded = true
                if (remoteSettings != null) {
                    prefs.applyAccountSyncedSettings(remoteSettings.toString())
                } else {
                    val local = prefs.accountSyncedSettings.first()
                    val uploaded = auth.saveAccountAppSettings(org.json.JSONObject(local))
                    initialUploadSucceeded = uploaded.isSuccess
                    if (!uploaded.isSuccess) android.util.Log.w("ToriAccountSync", "No se pudo crear el estado inicial de la cuenta", uploaded.exceptionOrNull())
                }
                var lastSyncedLocal = if (initialUploadSucceeded) prefs.accountSyncedSettings.first() else ""
                var lastSeenRemote = remoteSettings?.toString() ?: if (initialUploadSucceeded) lastSyncedLocal else ""
                while (true) {
                    delay(30_000)
                    var localNow = prefs.accountSyncedSettings.first()
                    if (localNow != lastSyncedLocal) {
                        val saved = auth.saveAccountAppSettings(org.json.JSONObject(localNow))
                        if (saved.isSuccess) {
                            lastSyncedLocal = localNow
                            lastSeenRemote = localNow
                        } else {
                            android.util.Log.w("ToriAccountSync", "No se pudo subir la apariencia", saved.exceptionOrNull())
                        }
                    }
                    val latestRemote = auth.getAccountAppSettings()
                    val latestSettings = latestRemote.getOrNull()
                    val latestRaw = latestSettings?.toString()
                    if (latestRemote.isSuccess && latestRaw != null && latestRaw != lastSeenRemote) {
                        localNow = prefs.accountSyncedSettings.first()
                        if (localNow == lastSyncedLocal) {
                            prefs.applyAccountSyncedSettings(latestRaw)
                            lastSyncedLocal = prefs.accountSyncedSettings.first()
                        } else {
                            val saved = auth.saveAccountAppSettings(org.json.JSONObject(localNow))
                            if (saved.isSuccess) lastSyncedLocal = localNow
                        }
                        lastSeenRemote = latestRaw
                    } else if (latestRemote.isSuccess && latestRaw == null && localNow != lastSyncedLocal) {
                        val saved = auth.saveAccountAppSettings(org.json.JSONObject(localNow))
                        if (saved.isSuccess) {
                            lastSyncedLocal = localNow
                            lastSeenRemote = localNow
                        }
                    }
                }
            } else {
                android.util.Log.w("ToriAccountSync", "No se pudo leer la apariencia de la cuenta", initialRemote.exceptionOrNull())
            }
        }
    }

    val glass by prefs.liquidGlassEnabled.collectAsState(false)
    val appBackdrop = LocalAppBackdrop.current
    val performanceMode = LocalGlassPerformanceMode.current
    val motionSettings by prefs.motionSettings.collectAsState(initial = MotionSettings())
    val currentMotionSettings = rememberUpdatedState(motionSettings)
    val chatsEnabled by prefs.experimentalChatsEnabled.collectAsState(false)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var navCompact by remember { mutableStateOf(false) }
    val showNav = route in listOf(Screen.Home.route, Screen.Search.route, Screen.Library.route, Screen.Chats.route)
    val navItems = remember(chatsEnabled) { if (chatsEnabled) baseNavItems + NavItem(Screen.Chats, Icons.Filled.Forum, Icons.Outlined.Forum) else baseNavItems }
    var avatar by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route) { auth.getProfile().onSuccess { avatar = it?.avatarUrl } }

    val playerDock = remember { PlayerDock() }
    val bitChordDock = remember { com.music.bitchord.ui.player.PlayerDock() }
    val playerMotionScope = rememberCoroutineScope()
    val routeState = rememberUpdatedState(route)
    val playerSheetMotion = remember(playerDock) {
        PlayerSheetMotion(
            scope = playerMotionScope,
            dock = playerDock,
            shown = { routeState.value == Screen.Player.route },
            setShown = { next ->
                if (next && routeState.value != Screen.Player.route) nav.navigate(Screen.Player.route)
                else if (!next && routeState.value == Screen.Player.route) nav.popBackStack()
            },
            motionSettings = { ToriMotionEngine.effectiveSettings(context, currentMotionSettings.value) },
        )
    }
    val playerWindowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    playerSheetMotion.windowInfo = playerWindowInfo
    SideEffect {
        val travel = playerWindowInfo.containerSize.height.toFloat()
        playerDock.sheetTravel = travel
        bitChordDock.sheetTravel = travel
        playerDock.attached = route == Screen.Player.route
        bitChordDock.sheetOffset = { playerSheetMotion.position() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(
            LocalPlayerDock provides playerDock,
            LocalMiniPlayerPull provides playerSheetMotion,
            com.music.bitchord.ui.player.LocalPlayerDock provides bitChordDock,
        ) {
        // SharedTransition removed — caused NoSuchMethodError on FullPlayer
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        if (targetState.destination.route == Screen.Player.route || initialState.destination.route == Screen.Player.route) EnterTransition.None
                        else {
                            val duration = ToriMotionEngine.transitionDurationMillis(motionSettings, 220)
                            fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 18 }
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.route == Screen.Player.route || initialState.destination.route == Screen.Player.route) ExitTransition.None
                        else {
                            val duration = ToriMotionEngine.transitionDurationMillis(motionSettings, 180)
                            fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { -it / 24 }
                        }
                    },
                    popEnterTransition = {
                        if (targetState.destination.route == Screen.Player.route || initialState.destination.route == Screen.Player.route) EnterTransition.None
                        else {
                            val duration = ToriMotionEngine.transitionDurationMillis(motionSettings, 220)
                            fadeIn(tween(duration)) + slideInVertically(tween(duration)) { -it / 20 }
                        }
                    },
                    popExitTransition = {
                        if (targetState.destination.route == Screen.Player.route || initialState.destination.route == Screen.Player.route) ExitTransition.None
                        else {
                            val duration = ToriMotionEngine.transitionDurationMillis(motionSettings, 180)
                            fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 24 }
                        }
                    }
                ) {
            composable(Screen.Auth.route) {
                LoginScreen(repository = auth, onContinueAsGuest = { nav.popBackStack() }, onLoginSuccess = { nav.popBackStack() })
            }
            composable(Screen.Profile.route) {
                ProfileScreen(repository = auth, onBack = { nav.popBackStack() }, onSignedOut = { nav.navigate(Screen.Auth.route) { popUpTo(0) } })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onSongClick = { PlayerController.init(context); PlayerController.playSong(it); playerSheetMotion.open() },
                    onOpenPlayer = { playerSheetMotion.open() },
                    onOpenSettings = { nav.navigate(Screen.Settings.route) },
                    onOpenProfile = { nav.navigate(if (auth.session.value == null) Screen.Auth.route else Screen.Profile.route) },
                    onOpenLibrary = { nav.navigate(Screen.Library.route) },
                    onOpenSearch = { nav.navigate(Screen.Search.route) },
                    onScrollStateChanged = { navCompact = it },
                    authRepository = auth
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    authRepository = auth,
                    isVisible = route == Screen.Search.route,
                    onSongClick = { PlayerController.init(context); PlayerController.playSong(it); playerSheetMotion.open() },
                    onPlayQueue = { songs, index -> PlayerController.init(context); PlayerController.playQueue(songs, index); playerSheetMotion.open() },
                    onUserClick = { username -> nav.navigate(Screen.PublicProfile.create(username)) },
                    onAlbumClick = { album -> nav.navigate(Screen.Album.create(album.name, album.artist)) }
                )
            }
            composable(Screen.PublicProfile.route) { entry ->
                val username = entry.arguments?.getString("username").orEmpty()
                com.pokke.music.ui.profile.PublicProfileScreen(
                    username = username,
                    repository = auth,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Screen.PublicPlaylist.route) { entry ->
                val playlistId = entry.arguments?.getString("playlistId").orEmpty()
                PublicPlaylistScreen(
                    playlistId = playlistId, repository = auth, onBack = { nav.popBackStack() },
                    onPlay = { songs, index -> PlayerController.init(context); PlayerController.playQueue(songs, index); playerSheetMotion.open() }
                )
            }
            composable(Screen.SharedSong.route) { entry ->
                val songId = entry.arguments?.getString("songId").orEmpty()
                var failed by remember(songId) { mutableStateOf(false) }
                LaunchedEffect(songId) {
                    MusicRepository().getSongById(songId).onSuccess { song ->
                        if (song != null) {
                            PlayerController.init(context); PlayerController.playSong(song)
                            nav.navigate(Screen.Player.route) { popUpTo(Screen.SharedSong.route) { inclusive = true } }
                        } else failed = true
                    }.onFailure { failed = true }
                }
                when {
                    failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No se encontró esta canción.", color = MaterialTheme.colorScheme.onBackground) }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                }
            }
            composable(Screen.SharedLyrics.route) { entry ->
                val songId = entry.arguments?.getString("songId").orEmpty()
                var linkedSong by remember(songId) { mutableStateOf<com.pokke.music.data.model.Song?>(null) }
                var failed by remember(songId) { mutableStateOf(false) }
                LaunchedEffect(songId) {
                    MusicRepository().getSongById(songId).onSuccess { song ->
                        linkedSong = song
                        if (song != null) { PlayerController.init(context); PlayerController.playSong(song) } else failed = true
                    }.onFailure { failed = true }
                }
                when {
                    linkedSong != null -> LyricsScreen(onBack = { nav.popBackStack() }, songOverride = linkedSong)
                    failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No se encontró la letra o la canción.", color = MaterialTheme.colorScheme.onBackground) }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                }
            }
            composable(Screen.Library.route) {
                LibraryScreen(repository = auth, isVisible = route == Screen.Library.route,
                    onLoginRequired = { nav.navigate(Screen.Auth.route) },
                    onSongClick = { PlayerController.init(context); PlayerController.playSong(it); playerSheetMotion.open() },
                    onPlayQueue = { songs, index ->
                        PlayerController.init(context)
                        PlayerController.playQueue(songs, index)
                        playerSheetMotion.open()
                    })
            }
            composable(Screen.Chats.route) {
                if (chatsEnabled) ChatScreen(authRepository = auth, onOpenMaps = { nav.navigate(Screen.Maps.route) }, onOpenStory = { nav.navigate(Screen.StoryPublish.route) })
                else SettingsScreen(onBack = { nav.popBackStack() }, authRepository = auth)
            }
            composable(Screen.Maps.route) {
                FriendsMapScreen(authRepository = auth, onBack = { nav.popBackStack() })
            }
            composable(Screen.StoryPublish.route) {
                StoryPublisherScreen(authRepository = auth, onBack = { nav.popBackStack() }, onPublished = { nav.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAdmin = { nav.navigate(Screen.Admin.route) },
                    authRepository = auth,
                    pluginDeepLinkUri = pluginDeepLinkUri,
                    onPluginDeepLinkHandled = onPluginDeepLinkHandled,
                    onRestartOnboarding = onRestartOnboarding,
                    onOpenRemoteControl = { nav.navigate(Screen.RemoteControl.route) }
                )
            }
            composable(Screen.RemoteControl.route) {
                com.pokke.music.ui.remote.RemoteControlScreen(onBack = { nav.popBackStack() })
            }
            composable(Screen.Admin.route) {
                AdminPanelScreen(repository = auth, onBack = { nav.popBackStack() })
            }
            composable(Screen.Player.route) {
                PlayerMotionSheet(
                    dock = playerDock,
                    motion = playerSheetMotion,
                ) { closePlayer ->
                    PlayerScreenPersistenceGate {
                        BitChordNowPlayingHost(
                            authRepository = auth,
                            onClose = closePlayer,
                            onOpenQueue = { nav.navigate(Screen.Queue.route) },
                            onOpenLyrics = { nav.navigate(Screen.Lyrics.route) },
                            onRequireLogin = { nav.navigate(Screen.Auth.route) },
                            onOpenArtist = { name ->
                                closePlayer()
                                nav.navigate(Screen.Artist.create(name))
                            },
                        )
                    }
                }
            }
            composable(Screen.Queue.route) { QueueScreen(onBack = { nav.popBackStack() }) }
            composable(
                route = Screen.Artist.route,
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name").orEmpty())
                ArtistScreen(
                    artistName = name.ifBlank { "Artista" },
                    onBack = { nav.popBackStack() },
                    onSongClick = { PlayerController.init(context); playerSheetMotion.open() },
                    onAlbumClick = { album, artist -> nav.navigate(Screen.Album.create(album, artist)) }
                )
            }

            composable(
                route = Screen.Album.route,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("artist") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name").orEmpty())
                val artist = android.net.Uri.decode(entry.arguments?.getString("artist").orEmpty()).ifBlank { null }
                AlbumScreen(
                    albumName = name,
                    artistName = artist,
                    onBack = { nav.popBackStack() },
                    onSongClick = { PlayerController.init(context); playerSheetMotion.open() }
                )
            }
            composable(Screen.Lyrics.route) { LyricsScreen(onBack = { nav.popBackStack() }) }
                }
            }

        val currentSong by PlayerController.currentSong.collectAsState()
        val showMini = currentSong != null && route !in listOf(Screen.Lyrics.route, Screen.Auth.route)
        MiniPlayerBar(
            visible = showMini,
            glass = glass,
            compact = navCompact,
            onOpenPlayer = { playerSheetMotion.open() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showNav) 78.dp else 16.dp)
                .navigationBarsPadding()
        )

        val navEnterMs = ToriMotionEngine.transitionDurationMillis(motionSettings, 180)
        val navExitMs = ToriMotionEngine.transitionDurationMillis(motionSettings, 140)
        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(navEnterMs)) + slideInVertically(tween(navEnterMs)) { it / 3 },
            exit = fadeOut(tween(navExitMs)) + slideOutVertically(tween(navExitMs)) { it / 3 }
        ) {
            // Liquid Glass bottom bar (Kyant-inspired surface when toggle ON)
            Row(
                modifier = Modifier.padding(bottom = 10.dp).navigationBarsPadding()
                    .animateContentSize(tween(ToriMotionEngine.transitionDurationMillis(motionSettings, 190)))
                    .clip(RoundedCornerShape(40.dp))
                    .then(
                        if (glass && appBackdrop != null) Modifier.drawBackdrop(
                            backdrop = appBackdrop,
                            shape = { Capsule() },
                            effects = { vibrancy(); blur((if (performanceMode) 8f else 14f).dp.toPx()); lens((if (performanceMode) 8f else 12f).dp.toPx(), (if (performanceMode) 14f else 22f).dp.toPx()) },
                            onDrawSurface = { drawRect(Color(0xB31B1B23)) }
                        ) else Modifier.background(if (glass) Color(0xCC292930) else Color(0xFF19191E))
                    )
                    .padding(horizontal = if (navCompact) 6.dp else 9.dp, vertical = if (navCompact) 5.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                (if (navCompact) baseNavItems else navItems).forEach { item ->
                    val selected = route == item.screen.route
                    val interaction = remember(item.screen.route) { MutableInteractionSource() }
                    val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "nav-icon-${item.screen.route}")
                    Column(
                        Modifier
                            .width(if (navCompact) 48.dp else 58.dp)
                            .height(if (navCompact) 44.dp else 54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Color(0x24FC3C44) else Color.Transparent)
                            .pokkePressMotion(interaction, pressedScale = 0.90f)
                            .clickable(interactionSource = interaction, indication = null) {
                                nav.navigate(item.screen.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (selected) item.selected else item.normal,
                            item.screen.route,
                            tint = if (selected) Color(0xFFFC3C44) else Color.White.copy(0.78f),
                            modifier = Modifier.size(if (navCompact) 21.dp else 22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                        if (!navCompact) {
                            Text(
                                when (item.screen) {
                                    Screen.Home -> "Home"
                                    Screen.Library -> "Library"
                                    Screen.Search -> "Search"
                                    Screen.Chats -> "Chats"
                                    else -> item.screen.route
                                },
                                color = if (selected) Color(0xFFFC3C44) else Color.White.copy(0.72f),
                                fontSize = 9.sp,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (!navCompact) {
                    val profileInteraction = remember { MutableInteractionSource() }
                    Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF29292E))
                        .pokkePressMotion(profileInteraction, pressedScale = 0.90f)
                        .clickable(interactionSource = profileInteraction, indication = null) { nav.navigate(if (auth.session.value == null) Screen.Auth.route else Screen.Profile.route) }, contentAlignment = Alignment.Center) {
                    if (!avatar.isNullOrBlank()) AsyncImage(avatar, "Perfil", Modifier.fillMaxSize().clip(CircleShape))
                    else Icon(Icons.Default.Person, "Perfil", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
        }
    }
}
