package com.pokke.music.ui.auth

import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.model.UserProfile
import com.pokke.music.data.preferences.UserPreferences
import com.pokke.music.ui.liquid.pokkePressMotion
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.UserEmblemRepository
import com.pokke.music.ui.profile.AvatarFrames
import com.pokke.music.ui.profile.FramedAvatar
import com.pokke.music.ui.profile.ListeningCard
import com.pokke.music.ui.profile.LinkFavicon
import com.pokke.music.ui.profile.hostLabel
import com.pokke.music.ui.profile.platformLabel
import com.pokke.music.ui.profile.MangaFavoritesEditor
import com.pokke.music.ui.profile.MangaFavoritesDisplay
import kotlinx.coroutines.launch

private val ProfileInk = Color(0xFFF3F2FA)
private val ProfileMuted = Color(0xFFA5A4B5)

@Composable
fun ProfileScreen(repository: AuthRepository, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val session by repository.session.collectAsState()
    val scope = rememberCoroutineScope()
    var hasDeveloperEmblem by remember { mutableStateOf(false) }
    val emblemRepository = remember { UserEmblemRepository() }
    LaunchedEffect(session?.userId) {
        hasDeveloperEmblem = session?.let { emblemRepository.hasDeveloperEmblem(it) } ?: false
    }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("midnight") }
    var frame by remember { mutableStateOf("none") }
    var badge by remember { mutableStateOf("none") }
    var pronouns by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var accent by remember { mutableStateOf("red") }
    var cardStyle by remember { mutableStateOf("glass") }
    var badges by remember { mutableStateOf("Pokke listener,Music lover") }
    var primaryColor by remember { mutableStateOf("#FC3C44") }
    var secondaryColor by remember { mutableStateOf("#1A1C26") }
    var gradientEnabled by remember { mutableStateOf(true) }
    var customBg by remember { mutableStateOf("") }
    var nameplateStyle by remember { mutableStateOf("aurora") }
    var nameFont by remember { mutableStateOf("sans") }
    var nameColor by remember { mutableStateOf("#F3F2FA") }
    var profileCardOrder by remember { mutableStateOf("listening,badges,mangas") }
    var isPublic by remember { mutableStateOf(true) }
    var showListening by remember { mutableStateOf(true) }
    var favoriteMangasJson by remember { mutableStateOf("[]") }
    var linkLabel by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var profileLinks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val liquidGlass by prefs.liquidGlassEnabled.collectAsState(initial = false)
    var uploadingMedia by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingMedia = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No se pudo leer la imagen")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"
                    else -> "jpg"
                }
                repository.uploadPublicImage("avatar", "avatar.$ext", bytes, mime).getOrThrow()
            }.onSuccess { avatar = it; message = "Avatar actualizado" }
                .onFailure { message = it.message ?: "Error al subir avatar" }
            uploadingMedia = false
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingMedia = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No se pudo leer la imagen")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"
                    else -> "jpg"
                }
                repository.uploadPublicImage("banner", "banner.$ext", bytes, mime).getOrThrow()
            }.onSuccess { banner = it; message = "Banner actualizado" }
                .onFailure { message = it.message ?: "Error al subir banner" }
            uploadingMedia = false
        }
    }
    val pickCustomBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingMedia = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No se pudo leer la imagen")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                repository.uploadPublicImage("bg", "custom_bg.jpg", bytes, mime).getOrThrow()
            }.onSuccess { customBg = it; message = "Fondo custom actualizado" }
                .onFailure { message = it.message ?: "Error al subir fondo" }
            uploadingMedia = false
        }
    }
    val pickCardBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingMedia = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No se pudo leer la imagen")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                repository.uploadPublicImage("card", "search_card.jpg", bytes, mime).getOrThrow()
            }.onSuccess { url ->
                repository.updateProfile(
                    displayName, username, bio, avatar, banner, theme, frame, badge, pronouns, status,
                    accent, cardStyle, badges, isPublic, profileLinks.firstOrNull()?.first ?: linkLabel, profileLinks.firstOrNull()?.second ?: linkUrl, showListening,
                    org.json.JSONArray().apply { profileLinks.drop(1).forEach { (label, url) -> put(org.json.JSONObject().put("label", label).put("url", url)) } }.toString(), favoriteMangasJson,
                    searchCardBannerUrl = url
                ).onSuccess { profile = it; message = "Nameplate del search actualizado" }
                    .onFailure { message = it.message }
            }.onFailure { message = it.message ?: "Error al subir nameplate" }
            uploadingMedia = false
        }
    }


    LaunchedEffect(session?.userId) {
        loading = true
        repository.getProfile().onSuccess { p ->
            profile = p
            displayName = p?.displayName.orEmpty(); username = p?.username.orEmpty(); bio = p?.bio.orEmpty()
            avatar = p?.avatarUrl.orEmpty(); banner = p?.bannerUrl.orEmpty(); theme = p?.theme ?: "midnight"
            frame = p?.frame ?: "none"; badge = p?.badge ?: "none"; pronouns = p?.pronouns.orEmpty()
            status = p?.statusText.orEmpty(); accent = p?.accentColor ?: "red"; cardStyle = p?.cardStyle ?: "glass"
            badges = p?.badges ?: "Pokke listener,Music lover"; isPublic = p?.isPublic ?: true
            primaryColor = p?.primaryColor ?: "#FC3C44"
            secondaryColor = p?.secondaryColor ?: "#1A1C26"
            gradientEnabled = p?.gradientEnabled ?: true
            customBg = p?.customBgUrl.orEmpty()
            nameplateStyle = p?.nameplateStyle?.takeIf { it in setOf("aurora", "neon", "royal", "minimal", "cyber") } ?: "aurora"
            nameFont = p?.nameFont?.takeIf { it in setOf("sans", "serif", "mono", "cursive") } ?: "sans"
            nameColor = p?.nameColor?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#F3F2FA"
            profileCardOrder = p?.profileCardOrder?.split(',')?.filter { it in setOf("listening", "badges", "mangas") }?.distinct()?.let { (it + listOf("listening", "badges", "mangas")).distinct().joinToString(",") } ?: "listening,badges,mangas"
            showListening = p?.showListening ?: true
            favoriteMangasJson = p?.favoriteMangas ?: "[]"
            linkLabel = p?.linkLabel.orEmpty(); linkUrl = p?.linkUrl.orEmpty()
            val primaryLinks = if (!p?.linkUrl.isNullOrBlank()) listOf((p?.linkLabel.orEmpty().ifBlank { "Link" }) to p!!.linkUrl!!) else emptyList()
            val savedExtraLinks = runCatching {
                val raw = p?.extraLinks.orEmpty()
                if (raw.trimStart().startsWith("[")) {
                    val arr = org.json.JSONArray(raw)
                    (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o ->
                        val label = o.optString("label").trim(); val url = o.optString("url").trim()
                        if (url.isNotBlank()) (label.ifBlank { "Link" }) to url else null
                    } }
                } else raw.split(',').mapNotNull { item ->
                    val parts = item.split('|', limit = 2)
                    if (parts.size == 2 && parts[1].isNotBlank()) parts[0].ifBlank { "Link" } to parts[1] else null
                }
            }.getOrDefault(emptyList())
            profileLinks = (primaryLinks + savedExtraLinks).distinctBy { it.second }
        }.onFailure { message = it.localizedMessage }
        loading = false
    }

    val saveProfileNow: () -> Unit = {
        if (!saving && !loading) {
            saving = true
            message = null
            scope.launch {
                repository.updateProfile(
                    displayName, username, bio, avatar, banner, theme, frame, badge, pronouns, status,
                    accent, cardStyle, badges, isPublic,
                    profileLinks.firstOrNull()?.first ?: linkLabel,
                    profileLinks.firstOrNull()?.second ?: linkUrl,
                    showListening,
                    org.json.JSONArray().apply {
                        profileLinks.drop(1).forEach { (label, url) ->
                            put(org.json.JSONObject().put("label", label).put("url", url))
                        }
                    }.toString(),
                    favoriteMangasJson,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    gradientEnabled = gradientEnabled,
                    customBgUrl = customBg,
                    nameplateStyle = nameplateStyle,
                    nameFont = nameFont,
                    nameColor = nameColor,
                    profileCardOrder = profileCardOrder
                ).onSuccess { profile = it; message = "Cambios guardados correctamente" }
                    .onFailure { message = it.localizedMessage ?: "No se pudo guardar el perfil" }
                saving = false
            }
        }
    }
    val shareProfileNow: () -> Unit = {
        val shareText = "Mira mi perfil en Pokke: pokke://user/${session?.userId.orEmpty()}"
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir perfil"))
        }.onFailure { message = it.localizedMessage ?: "No se pudo compartir el perfil" }
    }

    val accentColor = when (accent) {
        "red" -> Color(0xFFFC3C44); "blue" -> Color(0xFF8CB7FF); "pink" -> Color(0xFFFF9FCB); "cyan" -> Color(0xFF7EDCEB)
        "green" -> Color(0xFF8ED9B0); "gold" -> Color(0xFFE5C17A); else -> Color(0xFFAAA0FF)
    }
    val pageBackground = when (theme) {
        "sunset" -> Color(0xFF2D1B0E)
        "ocean" -> Color(0xFF101B2C)
        "violet" -> Color(0xFF1B142A)
        "mint" -> Color(0xFF0A1F1A)
        "gold" -> Color(0xFF1A1508)
        "rose" -> Color(0xFF291824)
        "cyber" -> Color(0xFF050510)
        "ember" -> Color(0xFF1A0C05)
        "amoled" -> Color.Black
        else -> Color(0xFF171A28)
    }
    val cardBackground = when (cardStyle) {
        "solid" -> if (liquidGlass) Color(0xCC252A3D) else Color(0xFF252A3D); "outline" -> pageBackground.copy(alpha = .55f); else -> if (liquidGlass) Color(0xAA252A40) else Color(0xCC252A40)
    }

    val pageBrush = if (gradientEnabled) {
        val pCol = try { Color(android.graphics.Color.parseColor(primaryColor)) } catch (_: Exception) { Color(0xFFAAA0FF) }
        val sCol = try { Color(android.graphics.Color.parseColor(secondaryColor)) } catch (_: Exception) { Color(0xFF1A1C26) }
        Brush.verticalGradient(listOf(sCol.copy(alpha=0.9f), pageBackground, Color(0xFF0A0A0C)))
    } else null
    Column(Modifier.fillMaxSize().animateContentSize().then(
        if (customBg.ifBlank { profile?.customBgUrl.orEmpty() }.isNotBlank()) Modifier.background(Color(0xFF0A0A0C))
        else if (pageBrush != null) Modifier.background(pageBrush)
        else Modifier.background(pageBackground)
    ).verticalScroll(rememberScrollState())) {
        // Full-page custom background sits behind everything when set
        val bgFull = customBg.ifBlank { profile?.customBgUrl.orEmpty() }
        if (bgFull.isNotBlank()) {
            // drawn in outer box via parent - header uses banner separately
        }
        Box(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).clickable { pickBanner.launch("image/*") }) {
            // Banner is ONLY the header strip (not the same as custom bg)
            if (banner.isNotBlank()) {
                AsyncImage(banner, "Banner del perfil", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (bgFull.isNotBlank()) {
                AsyncImage(bgFull, "Fondo custom (header)", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accentColor.copy(.78f), Color(0xFF20243B), pageBackground))))
            }
            // Primary/secondary gradient always on top when enabled
            if (gradientEnabled) {
                val pCol = try { Color(android.graphics.Color.parseColor(primaryColor)) } catch (_: Exception) { Color(0xFFAAA0FF) }
                val sCol = try { Color(android.graphics.Color.parseColor(secondaryColor)) } catch (_: Exception) { Color(0xFF1A1C26) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pCol.copy(alpha = 0.55f), sCol.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.55f)))))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.05f), Color.Black.copy(.35f)))))
            Surface(
                color = Color.Black.copy(.42f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
            ) { Text("Cambiar banner", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileTopButton(onClick = { if (editing) editing = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text("Editar perfil", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = shareProfileNow) { Text("Compartir", color = Color.White) }
                TextButton(onClick = saveProfileNow, enabled = !saving && !loading) { Text(if (saving) "Guardando…" else "Guardar", color = Color(0xFFFC3C44), fontWeight = FontWeight.Bold) }
                ProfileTopButton(onClick = { scope.launch { repository.signOut(); onSignedOut() } }) { Icon(Icons.Default.Logout, "Cerrar sesión", tint = Color.White) }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp).clickable { pickAvatar.launch("image/*") }) {
                FramedAvatar(
                    avatarUrl = avatar.ifBlank { null },
                    displayName = displayName,
                    frameId = frame,
                    size = 48.dp,
                    borderColor = accentColor,
                    borderWidth = 3.dp
                )
                if (badge != "none") {
                    Surface(
                        Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 2.dp),
                        shape = CircleShape,
                        color = accentColor
                    ) {
                        Icon(Icons.Default.Badge, "Emblema", tint = Color(0xFF171A28), modifier = Modifier.padding(6.dp).size(16.dp))
                    }
                }
            }
        }

        if (!editing) Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val displayNameColor = runCatching { Color(android.graphics.Color.parseColor(nameColor)) }.getOrDefault(ProfileInk)
                val displayNameFont = when (nameFont) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; "cursive" -> FontFamily.Cursive; else -> FontFamily.SansSerif }
                val customPlateStart = runCatching { Color(android.graphics.Color.parseColor(profile?.nameplatePrimaryColor ?: "")) }.getOrNull()
                val customPlateEnd = runCatching { Color(android.graphics.Color.parseColor(profile?.nameplateSecondaryColor ?: "")) }.getOrNull()
                val nameplateBrush = if (!profile?.nameplateId.isNullOrBlank() && customPlateStart != null && customPlateEnd != null) Brush.horizontalGradient(listOf(customPlateStart, customPlateEnd)) else when (nameplateStyle) {
                    "neon" -> Brush.horizontalGradient(listOf(Color(0xFF17102D), Color(0xFF6C3BBD), Color(0xFF17102D)))
                    "royal" -> Brush.horizontalGradient(listOf(Color(0xFF34230C), Color(0xFFB88A3D), Color(0xFF34230C)))
                    "minimal" -> Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
                    "cyber" -> Brush.horizontalGradient(listOf(Color(0xFF3B101C), Color(0xFFB52B56), Color(0xFF1A1018)))
                    else -> Brush.horizontalGradient(listOf(Color(0xFF202C4A), Color(0xFF7658B5), Color(0xFF202C4A)))
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(nameplateBrush).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    if (!profile?.nameplateImageUrl.isNullOrBlank()) {
                        AsyncImage(profile?.nameplateImageUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.52f), Color.Transparent))))
                    }
                    Text(displayName.ifBlank { "Tu perfil" }, color = displayNameColor, fontFamily = displayNameFont, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!profile?.nameplateId.isNullOrBlank()) {
                    Text("NAMEPLATE PERSONALIZADO", color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                if (hasDeveloperEmblem) {
                    Surface(color = Color(0xFFA78BFA).copy(alpha = .18f), contentColor = Color(0xFFA78BFA), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Code, contentDescription = "Desarrollador", modifier = Modifier.size(14.dp))
                            Text("Dev", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${username.ifBlank { "usuario" }}", color = ProfileMuted, fontSize = 14.sp)
                if (pronouns.isNotBlank()) Text("  ·  $pronouns", color = ProfileMuted, fontSize = 13.sp)
            }
            if (status.isNotBlank()) {
                Surface(color = accentColor.copy(.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Text(status, color = accentColor, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            if (badges.isNotBlank()) Row(Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                badges.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(5).forEach { label ->
                    Surface(color = accentColor.copy(.13f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = accentColor, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp)); Text(label, color = ProfileInk, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (bio.isNotBlank()) Text(bio, color = ProfileInk.copy(.88f), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 17.dp))
            if (linkUrl.isNotBlank()) Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinkFavicon(linkUrl, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        linkLabel.ifBlank { platformLabel(linkUrl) ?: hostLabel(linkUrl) },
                        color = ProfileInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(hostLabel(linkUrl), color = ProfileMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF171827))) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Editar perfil principal", fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(editing, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(cardBackground).padding(16.dp)) {
                    Text("Tema del perfil", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    ProfileChoice("Tema", theme, listOf("midnight", "sunset", "ocean", "violet", "mint", "gold", "rose", "cyber", "ember"), listOf("Noche", "Atardecer", "Océano", "Violeta", "Menta", "Dorado", "Rosa", "Cyber", "Brasas"), Color(0xFFFC3C44)) { theme = it }
                    Spacer(Modifier.height(14.dp))
                    Text("Efecto del perfil", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("none" to "Ninguno", "glass" to "Glass", "parallax" to "Parallax", "glow" to "Glow").forEach { (value, label) ->
                            val selected = cardStyle == value || (value == "none" && cardStyle == "solid")
                            Surface(
                                onClick = {
                                    cardStyle = value
                                    if (value == "parallax") scope.launch { prefs.setMotionParallax(true) }
                                    if (value == "glow") scope.launch { prefs.setMotionReflections(true) }
                                },
                                color = if (selected) Color(0xFFFC3C44) else Color.White.copy(.09f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) { Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp)) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Editar perfil", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    ProfileField("Nombre visible", displayName) { displayName = it }
                    ProfileField("Nombre de usuario (3–24 caracteres)", username) { username = it }
                    ProfileField("Pronombres", pronouns) { pronouns = it }
                    ProfileField("Estado personalizado", status) { status = it }
                    ProfileField("Biografía", bio, false) { bio = it }
                    Spacer(Modifier.height(12.dp))
                    Text("IDENTIDAD VISUAL", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Plantillas predeterminadas", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("moshii" to "Moshii", "aurora" to "Aurora", "cyber" to "Cyber", "minimal" to "Minimal").forEach { (id, label) ->
                            Text(label, color = ProfileInk, fontSize = 11.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.08f)).clickable {
                                nameplateStyle = id
                                when (id) {
                                    "moshii" -> { accent = "red"; theme = "rose"; cardStyle = "glass"; primaryColor = "#FC3C44"; secondaryColor = "#1A0C0E" }
                                    "cyber" -> { accent = "pink"; cardStyle = "solid"; primaryColor = "#7B2448"; secondaryColor = "#1A1018" }
                                    "minimal" -> { accent = "blue"; cardStyle = "outline"; primaryColor = "#263247"; secondaryColor = "#10141C" }
                                    else -> { accent = "violet"; cardStyle = "glass"; primaryColor = "#7658B5"; secondaryColor = "#202C4A" }
                                }
                            }.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Text("Nameplate", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("aurora" to "Aurora", "neon" to "Neon", "royal" to "Royal", "minimal" to "Minimal", "cyber" to "Cyber").forEach { (id, label) ->
                            val selected = nameplateStyle == id
                            Text(label, color = if (selected) Color.Black else ProfileInk, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) accentColor else Color.White.copy(.08f)).clickable { nameplateStyle = id }.padding(horizontal = 8.dp, vertical = 9.dp))
                        }
                    }
                    ProfileChoice("Tipografía del nombre", nameFont, listOf("sans", "serif", "mono", "cursive"), listOf("Sans", "Serif", "Mono", "Cursiva"), accentColor) { nameFont = it }
                    Text("Color del nombre", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("#F3F2FA", "#BBA5FF", "#83DFFF", "#FF9FCB", "#E8C77E", "#9CE0B5").forEach { hex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(ProfileInk)
                            Box(Modifier.size(30.dp).clip(CircleShape).background(color).border(if (nameColor == hex) 3.dp else 1.dp, if (nameColor == hex) Color.White else Color.White.copy(.25f), CircleShape).clickable { nameColor = hex })
                        }
                    }
                    Text("Orden de bloques del perfil", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Text(
                        "Mantén presionado un bloque y arrástralo para reordenar.",
                        color = ProfileMuted.copy(0.85f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    val orderedCards = remember(profileCardOrder) {
                        profileCardOrder.split(',')
                            .filter { it in setOf("listening", "badges", "mangas") }
                            .distinct()
                            .let { (it + listOf("listening", "badges", "mangas")).distinct() }
                    }
                    var draggingIndex by remember { mutableStateOf<Int?>(null) }
                    var dragOffsetY by remember { mutableStateOf(0f) }
                    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }

                    orderedCards.forEachIndexed { index, card ->
                        val isDragging = draggingIndex == index
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .zIndex(if (isDragging) 2f else 0f)
                                .offset {
                                    IntOffset(
                                        0,
                                        if (isDragging) dragOffsetY.roundToInt() else 0
                                    )
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isDragging) accentColor.copy(0.18f)
                                    else Color.White.copy(0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (isDragging) accentColor.copy(0.55f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .pointerInput(orderedCards, index) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                            val shift = (dragOffsetY / rowHeightPx).toInt()
                                            val to = (from + shift).coerceIn(0, orderedCards.lastIndex)
                                            if (to != from) {
                                                val mutable = orderedCards.toMutableList()
                                                val item = mutable.removeAt(from)
                                                mutable.add(to, item)
                                                profileCardOrder = mutable.joinToString(",")
                                                draggingIndex = to
                                                dragOffsetY -= (to - from) * rowHeightPx
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (card) {
                                    "listening" -> "Escuchando ahora"
                                    "badges" -> "Emblemas"
                                    else -> "Mangas favoritos"
                                },
                                color = ProfileInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("⠿", color = ProfileMuted, fontSize = 18.sp)
                        }
                    }
                    Text("Foto y banner", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickAvatar.launch("image/*") },
                            enabled = !uploadingMedia,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F42), contentColor = ProfileInk)
                        ) { Text(if (uploadingMedia) "…" else "Elegir avatar") }
                        Button(
                            onClick = { pickBanner.launch("image/*") },
                            enabled = !uploadingMedia,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F42), contentColor = ProfileInk)
                        ) { Text(if (uploadingMedia) "…" else "Elegir banner") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pickCardBanner.launch("image/*") },
                        enabled = !uploadingMedia,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F42), contentColor = ProfileInk)
                    ) { Text("Nameplate del search (galería)") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pickCustomBg.launch("image/*") },
                        enabled = !uploadingMedia,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F42), contentColor = ProfileInk)
                    ) { Text("Fondo custom del perfil (galería)") }
                    if (customBg.isNotBlank()) {
                        Text("Fondo listo · toca Guardar", color = ProfileMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Gradiente del perfil", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Usar gradiente primary/secondary", color = ProfileInk, fontSize = 13.sp)
                            Text("Si hay fondo custom, el gradiente se mezcla encima", color = ProfileMuted, fontSize = 11.sp)
                        }
                        Switch(gradientEnabled, { gradientEnabled = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Primary", color = ProfileMuted, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#AAA0FF","#8CB7FF","#FF9FCB","#8FE0B0","#E5C17A","#FFFFFF").forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(c)
                                    .clickable { primaryColor = hex }
                            )
                        }
                    }
                    Text("Secondary", color = ProfileMuted, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#1A1C26","#0B0B10","#1E1030","#102018","#2A1A10","#12141C").forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(c)
                                    .clickable { secondaryColor = hex }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ProfileField("Texto del enlace", linkLabel) { linkLabel = it }
                    ProfileField("URL del enlace", linkUrl) { linkUrl = it }
                    if (linkLabel.isNotBlank() && linkUrl.isNotBlank()) {
                        TextButton(onClick = {
                            profileLinks = profileLinks + (linkLabel to linkUrl)
                            linkLabel = ""; linkUrl = ""
                        }) { Text("+ Agregar enlace", color = accentColor) }
                    }
                    profileLinks.forEachIndexed { idx, (lab, url) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$lab · $url", color = ProfileInk, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { profileLinks = profileLinks.filterIndexed { i, _ -> i != idx } }) {
                                Text("Quitar", color = Color(0xFFFF8A80), fontSize = 12.sp)
                            }
                        }
                    }
                    MangaFavoritesEditor(favoriteMangasJson, { favoriteMangasJson = it }, accentColor, ProfileInk, ProfileMuted)
                    Text("Los emblemas los otorga el Owner desde Admin (no se escriben a mano).", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                    ProfileChoice("Color de acento", accent, listOf("red", "violet", "blue", "pink", "cyan", "green", "gold"), listOf("Rojo", "Violeta", "Azul", "Rosa", "Cian", "Verde", "Dorado"), accentColor) { accent = it }
                    Text("Marco del avatar", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                    FramePicker(selected = frame, accent = accentColor, onSelect = { frame = it })
                    ProfileChoice("Emblema principal", badge, listOf("none", "music", "star", "crown", "verified"), listOf("Ninguno", "Música", "Estrella", "Corona", "Verificado"), accentColor) { badge = it }
                    ProfileChoice("Estilo de tarjeta", cardStyle, listOf("glass", "solid", "outline"), listOf("Cristal", "Sólido", "Contorno"), accentColor) { cardStyle = it }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Perfil público", color = ProfileInk, fontWeight = FontWeight.SemiBold)
                            Text("Otros pueden buscarte y ver tu perfil", color = ProfileMuted, fontSize = 11.sp)
                        }
                        Switch(isPublic, { isPublic = it })
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mostrar lo que escucho", color = ProfileInk, fontWeight = FontWeight.SemiBold)
                            Text("Otros ven canción y progreso en tu perfil (por defecto activado)", color = ProfileMuted, fontSize = 11.sp)
                        }
                        Switch(showListening, {
                            showListening = it
                            scope.launch { prefs.setShowListeningOnProfile(it) }
                        })
                    }
                    if (message != null) Text(message!!, color = if (message!!.contains("correctamente")) Color(0xFF8FE0B0) else Color(0xFFFF9C9C), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = {
                        saving = true; message = null
                        scope.launch {
                            repository.updateProfile(
                                displayName, username, bio, avatar, banner, theme, frame, badge, pronouns, status,
                                accent, cardStyle, badges, isPublic, profileLinks.firstOrNull()?.first ?: linkLabel, profileLinks.firstOrNull()?.second ?: linkUrl, showListening,
                                org.json.JSONArray().apply { profileLinks.drop(1).forEach { (label, url) -> put(org.json.JSONObject().put("label", label).put("url", url)) } }.toString(), favoriteMangasJson,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                gradientEnabled = gradientEnabled,
                                customBgUrl = customBg,
                                nameplateStyle = nameplateStyle,
                                nameFont = nameFont,
                                nameColor = nameColor,
                                profileCardOrder = profileCardOrder
                            )
                                .onSuccess { profile = it; message = "Cambios guardados correctamente" }
                                .onFailure { message = it.localizedMessage ?: "No se pudo guardar el perfil" }
                            saving = false
                        }
                    }, enabled = !loading && !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF171827))) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF171827), strokeWidth = 2.dp) else Text("Guardar personalización", fontWeight = FontWeight.Bold)
                    }
                }
            }

        if (!editing) {
            Row(Modifier.fillMaxWidth().padding(top = 25.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTab("Principal", tab == 0, accentColor, Modifier.weight(1f)) { tab = 0 }
                ProfileTab("Favoritos", tab == 1, accentColor, Modifier.weight(1f)) { tab = 1 }
                ProfileTab("Acerca de", tab == 2, accentColor, Modifier.weight(1f)) { tab = 2 }
            }
            when (tab) {
                0 -> {
                    val orderedCards = profileCardOrder.split(',').filter { it in setOf("listening", "badges", "mangas") }.distinct().let { (it + listOf("listening", "badges", "mangas")).distinct() }
                    orderedCards.forEach { card ->
                        when (card) {
                            "listening" -> if (showListening) {
                                Spacer(Modifier.height(12.dp))
                                ListeningCard(accent = accentColor, cardBg = cardBackground, ink = ProfileInk, muted = ProfileMuted, live = true)
                            } else ProfilePanel(cardBackground, accentColor, "Actividad", "Escucha oculta", "Activaste la privacidad: otros no verán lo que escuchas.", Icons.Default.MusicNote)
                            "badges" -> ProfilePanel(cardBackground, accentColor, "Emblemas", "Colección del perfil", badges.ifBlank { "Todavía no has añadido emblemas." }, Icons.Default.Badge)
                            "mangas" -> {
                                Spacer(Modifier.height(12.dp))
                                MangaFavoritesDisplay(favoriteMangasJson, accentColor, ProfileInk, ProfileMuted, cardBackground)
                            }
                        }
                    }
                }
                1 -> {
                        var favs by remember { mutableStateOf<List<com.pokke.music.data.model.Song>>(emptyList()) }
                        LaunchedEffect(session?.userId) {
                            repository.getFavorites().onSuccess { favs = it }
                        }
                        if (favs.isEmpty()) {
                            ProfilePanel(cardBackground, accentColor, "Favoritos", "Tu colección", "Aún no hay canciones favoritas sincronizadas.", Icons.Default.Favorite)
                        } else {
                            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Text("Canciones favoritas", color = ProfileInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                                favs.take(30).forEach { s ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292929))) {
                                            s.coverUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(s.title, color = ProfileInk, fontSize = 14.sp, maxLines = 1)
                                            Text(s.artist, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                else -> {
                        ProfilePanel(cardBackground, accentColor, "Información", if (isPublic) "Perfil público" else "Perfil privado",
                            buildString {
                                append("Miembro de Pokke")
                                session?.email?.let { append(" · $it") }
                                if (bio.isNotBlank()) append("\n\n$bio")
                                if (pronouns.isNotBlank()) append("\nPronombres: $pronouns")
                            }, Icons.Default.Public)
                    }
            }
        }
        Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable private fun ProfileTopButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(.34f)).pokkePressMotion(interaction, pressedScale = .88f).clickable(interactionSource = interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center, content = { content() })
}

@Composable private fun ProfileField(label: String, value: String, multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ProfileInk, unfocusedTextColor = ProfileInk,
            focusedContainerColor = Color.White.copy(.085f), unfocusedContainerColor = Color.White.copy(.055f),
            focusedBorderColor = Color.White.copy(.34f), unfocusedBorderColor = Color.White.copy(.13f),
            focusedLabelColor = Color(0xFFFC3C44), unfocusedLabelColor = ProfileMuted, cursorColor = Color(0xFFFC3C44)))
}

@Composable private fun ProfileChoice(title: String, selected: String, values: List<String>, labels: List<String>, accent: Color, onSelect: (String) -> Unit) {
    Text(title, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 7.dp))
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(values.size) { index ->
            val value = values[index]
            val active = selected == value
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (active) accent.copy(.25f) else Color.White.copy(.055f))
                .border(if (active) 1.dp else 0.dp, if (active) accent else Color.Transparent, RoundedCornerShape(9.dp))
                .clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(labels[index], color = if (active) accent else ProfileMuted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable private fun ProfileTab(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) accent else ProfileMuted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(if (selected) 3.dp else 1.dp).clip(CircleShape).background(if (selected) accent else Color.White.copy(.10f)))
    }
}

@Composable private fun ProfilePanel(background: Color, accent: Color, title: String, heading: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(top = 15.dp).clip(RoundedCornerShape(20.dp)).background(background).padding(17.dp)) {
        Text(title, color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(heading, color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = ProfileMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

private fun frameColor(frame: String) = when (frame) {
    "red" -> Color(0xFFFC3C44); "blue" -> Color(0xFF8CB7FF); "pink" -> Color(0xFFFF9FCB); "gold" -> Color(0xFFE5C17A); else -> Color(0xFFAAA0FF)
}

@Composable
private fun FramePicker(selected: String, accent: Color, onSelect: (String) -> Unit) {
    val frames = AvatarFrames.ALL
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        items(frames.size) { index ->
            val f = frames[index]
            val active = selected == f.id
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (active) accent.copy(.22f) else Color.White.copy(.06f))
                        .border(if (active) 2.dp else 1.dp, if (active) accent else Color.White.copy(.12f), CircleShape)
                        .clickable { onSelect(f.id) },
                    contentAlignment = Alignment.Center
                ) {
                    FramedAvatar(
                        avatarUrl = null,
                        displayName = "P",
                        frameId = f.id,
                        size = 56.dp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (f.id == "none") "—" else f.id.removePrefix("frame_"),
                    color = if (active) accent else ProfileMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
