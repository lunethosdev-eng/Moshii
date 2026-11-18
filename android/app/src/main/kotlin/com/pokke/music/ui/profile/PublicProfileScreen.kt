package com.pokke.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.model.UserProfile
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.util.ShareLinks
import com.pokke.music.data.preferences.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFFF3F2FA)
private val Muted = Color(0xFFA5A4B5)

private fun pUsername(profile: UserProfile?, fallback: String): String? = profile?.username?.takeIf { it.isNotBlank() } ?: fallback.takeIf { it.isNotBlank() }

@Composable
fun PublicProfileScreen(
    username: String,
    repository: AuthRepository,
    onBack: () -> Unit
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var listening by remember { mutableStateOf<AuthRepository.ListeningPresence?>(null) }
    var favorites by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferences(context) }
    val liquidGlass by preferences.liquidGlassEnabled.collectAsState(initial = false)

    LaunchedEffect(username) {
        loading = true
        error = null
        repository.getProfileByUsername(username)
            .onSuccess { profile = it; if (it == null) error = "Perfil no encontrado o privado" }
            .onFailure { error = it.localizedMessage ?: "No se pudo cargar el perfil" }
        repository.getPublicFavorites(username).onSuccess { favorites = it }
        loading = false
    }

    // Poll presence while profile is open
    LaunchedEffect(profile?.id) {
        val uid = profile?.id ?: return@LaunchedEffect
        while (true) {
            if (profile?.showListening == true) {
                repository.getListeningForUser(uid).onSuccess { listening = it }
            } else listening = null
            delay(5000)
        }
    }

    val p = profile
    val accent = when (p?.accentColor) {
        "blue" -> Color(0xFF8CB7FF)
        "pink" -> Color(0xFFFF9FCB)
        "green" -> Color(0xFF8FE0B0)
        "violet" -> Color(0xFFB69CFF)
        else -> Color(0xFFE5C17A)
    }
    val cardBg = if (liquidGlass) Color(0xAA1A1A20) else Color(0xFF1A1A20)

    Box(Modifier.fillMaxSize().background(Color(0xFF09090B))) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            error != null -> Text(error!!, color = Color(0xFFFF8A80), modifier = Modifier.align(Alignment.Center).padding(24.dp))
            p != null -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(180.dp)) {
                        if (!p.bannerUrl.isNullOrBlank()) {
                            AsyncImage(p.bannerUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(.35f), Color(0xFF09090B)))))
                        }
                        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        if (!pUsername(p, username).isNullOrBlank()) {
                            IconButton(
                                onClick = { ShareLinks.share(context, ShareLinks.Kind.PROFILE, pUsername(p, username).orEmpty(), "Perfil de Pokke") },
                                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                            ) { Icon(Icons.Default.Share, "Compartir perfil", tint = Color.White) }
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp)) {
                        AsyncImage(
                            model = p.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp).clip(CircleShape).background(Color(0xFF22222A)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(12.dp))
                        val titleColor = runCatching { Color(android.graphics.Color.parseColor(p.nameColor ?: "#F3F2FA")) }.getOrDefault(Ink)
                        val titleFont = when (p.nameFont) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; "cursive" -> FontFamily.Cursive; else -> FontFamily.SansSerif }
                        val customPlateStart = runCatching { Color(android.graphics.Color.parseColor(p.nameplatePrimaryColor ?: "")) }.getOrNull()
                        val customPlateEnd = runCatching { Color(android.graphics.Color.parseColor(p.nameplateSecondaryColor ?: "")) }.getOrNull()
                        val plateBrush = if (!p.nameplateId.isNullOrBlank() && customPlateStart != null && customPlateEnd != null) Brush.horizontalGradient(listOf(customPlateStart, customPlateEnd)) else when (p.nameplateStyle) {
                            "neon" -> Brush.horizontalGradient(listOf(Color(0xFF17102D), Color(0xFF6C3BBD), Color(0xFF17102D)))
                            "royal" -> Brush.horizontalGradient(listOf(Color(0xFF34230C), Color(0xFFB88A3D), Color(0xFF34230C)))
                            "cyber" -> Brush.horizontalGradient(listOf(Color(0xFF3B101C), Color(0xFFB52B56), Color(0xFF1A1018)))
                            "minimal" -> Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
                            else -> Brush.horizontalGradient(listOf(Color(0xFF202C4A), Color(0xFF7658B5), Color(0xFF202C4A)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(plateBrush).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                if (!p.nameplateImageUrl.isNullOrBlank()) {
                                    AsyncImage(p.nameplateImageUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.5f), Color.Transparent))))
                                }
                                Text(p.displayName ?: p.username ?: username, color = titleColor, fontFamily = titleFont, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            if (!p.nameplateId.isNullOrBlank()) Text("CUSTOM", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("@${p.username ?: username}", color = Muted, fontSize = 14.sp)

                        // Real badge chips with icons
                        val badgeList = (p.badges ?: "").split(',').map { it.trim() }.filter { it.isNotBlank() }
                        if (badgeList.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(badgeList) { b ->
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF2A2418))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.EmojiEvents, null, tint = accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(b, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // 1) Listening first
                        if (p.showListening && listening != null) {
                            Spacer(Modifier.height(18.dp))
                            Text("Escuchando en Pokke", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            val song = Song(
                                id = listening!!.songId.orEmpty(),
                                title = listening!!.title.orEmpty(),
                                artist = listening!!.artist.orEmpty(),
                                coverUrl = listening!!.coverUrl,
                                audioUrl = listening!!.audioUrl
                            )
                            ListeningCard(
                                accent = accent,
                                cardBg = cardBg,
                                ink = Ink,
                                muted = Muted,
                                live = false,
                                fallbackSong = song,
                                fallbackPositionMs = listening!!.positionMs,
                                fallbackDurationMs = listening!!.durationMs.coerceAtLeast(1),
                                showLyrics = true
                            )
                        }

                        // 2) Mangas
                        MangaFavoritesDisplay(p.favoriteMangas, accent, Ink, Muted, cardBg)

                        // 3) Emblems section (collection)
                        if (badgeList.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text("Emblemas", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            badgeList.forEach { b ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(cardBg)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2418)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Shield, null, tint = accent)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(b, color = Ink, fontWeight = FontWeight.Medium)
                                        Text("Colección del perfil", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // 4) Public favorites
                        if (favorites.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text("Favoritos", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            favorites.take(12).forEach { song ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        song.coverUrl,
                                        null,
                                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF22222A)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(song.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }

                        if (!p.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text("About Me", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(p.bio!!, color = Ink.copy(.9f), fontSize = 14.sp, lineHeight = 20.sp)
                        }

                        val publicLinks = buildList {
                            if (!p.linkUrl.isNullOrBlank()) add((p.linkLabel.orEmpty().ifBlank { "Link" }) to p.linkUrl!!)
                            runCatching {
                                val raw = p.extraLinks.orEmpty()
                                if (raw.trimStart().startsWith("[")) {
                                    val arr = org.json.JSONArray(raw)
                                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o ->
                                        val url = o.optString("url").trim()
                                        if (url.isNotBlank()) add(o.optString("label").ifBlank { "Link" } to url)
                                    }
                                } else raw.split(',').forEach { item ->
                                    val parts = item.split('|', limit = 2)
                                    if (parts.size == 2 && parts[1].isNotBlank()) add(parts[0].ifBlank { "Link" } to parts[1])
                                }
                            }
                        }.distinctBy { it.second }
                        publicLinks.forEach { (label, url) ->
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinkFavicon(url, size = 28.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label.ifBlank { platformLabel(url) ?: hostLabel(url) }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(hostLabel(url), color = Muted, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}
