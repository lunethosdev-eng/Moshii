package com.pokke.music.ui.social

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.pokke.music.data.model.Song
import com.pokke.music.data.remote.GiphyClient
import com.pokke.music.data.remote.GiphyGif
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.MultiMusicRepository
import com.pokke.music.data.repository.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class GalleryItem(val uri: Uri, val isVideo: Boolean)
private data class StoryText(
    val text: String,
    val offset: Offset = Offset.Zero,
    val style: Int = 0,
    val isGif: Boolean = false,
    val gifUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPublisherScreen(
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onPublished: () -> Unit = {}
) {
    val context = LocalContext.current
    val social = remember(authRepository) { SocialRepository(authRepository) }
    val scope = rememberCoroutineScope()
    val multiMusic = remember { MultiMusicRepository() }

    var hasPermission by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var phase by remember { mutableStateOf("gallery") } // gallery | music | editor
    var caption by remember { mutableStateOf("") }
    var storyTexts by remember { mutableStateOf<List<StoryText>>(emptyList()) }
    var draftText by remember { mutableStateOf("") }
    var showTextEditor by remember { mutableStateOf(false) }
    var textStyle by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPublishSheet by remember { mutableStateOf(false) }
    var attachMusic by remember { mutableStateOf(false) }
    var musicOnly by remember { mutableStateOf(false) }
    var selectedStorySong by remember { mutableStateOf<Song?>(null) }
    var musicQuery by remember { mutableStateOf("") }
    var musicResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var musicLoading by remember { mutableStateOf(false) }
    var stickerOpen by remember { mutableStateOf(false) }
    var giphyQuery by remember { mutableStateOf("") }
    var giphyResults by remember { mutableStateOf<List<GiphyGif>>(emptyList()) }
    var giphyLoading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.any { it }
        if (hasPermission) {
            scope.launch {
                gallery = withContext(Dispatchers.IO) { loadGallery(context) }
            }
        }
    }

    fun ensurePermission() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val granted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            hasPermission = true
            scope.launch { gallery = withContext(Dispatchers.IO) { loadGallery(context) } }
        } else {
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(Unit) { ensurePermission() }

    LaunchedEffect(musicQuery) {
        if (phase != "music") return@LaunchedEffect
        if (musicQuery.isBlank()) {
            musicResults = emptyList()
            return@LaunchedEffect
        }
        musicLoading = true
        multiMusic.searchAll(musicQuery)
            .onSuccess { musicResults = it.take(40) }
            .onFailure { musicResults = emptyList() }
        musicLoading = false
    }

    LaunchedEffect(stickerOpen, giphyQuery) {
        if (!stickerOpen) return@LaunchedEffect
        giphyLoading = true
        runCatching {
            giphyResults = if (giphyQuery.isBlank()) GiphyClient.trending(30) else GiphyClient.search(giphyQuery, 30)
        }.onFailure { message = it.message }
        giphyLoading = false
    }

    fun toggleSelect(uri: Uri) {
        selected = when {
            selected.contains(uri) -> selected - uri
            selected.size >= 3 -> selected.drop(1) + uri
            else -> selected + uri
        }
    }

    suspend fun publish(audience: String) {
        if (selected.isEmpty() && !(musicOnly && selectedStorySong != null)) return
        busy = true
        message = null
        runCatching {
            val storySong = selectedStorySong
            val musicMeta = if (attachMusic && storySong != null) {
                " ♪ ${storySong.title} – ${storySong.artist}"
            } else ""
            if (musicOnly && storySong != null) {
                val cap = buildString {
                    if (audience == "friends") append("[friends] ")
                    append("[music_only] ")
                    append("${storySong.title} – ${storySong.artist}")
                    if (caption.isNotBlank()) append("\n$caption")
                    storyTexts.filter { !it.isGif }.forEach { append("\n${it.text}") }
                }
                val cover = storySong.coverUrl
                if (!cover.isNullOrBlank()) {
                    social.publishStory(cover, "image", cap).getOrThrow()
                } else error("Sin carátula para historia de solo música")
            } else {
                selected.forEachIndexed { index, uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No se pudo leer el archivo")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isVideo = mime.startsWith("video")
                    if (isVideo && bytes.size > 40 * 1024 * 1024) error("Vídeo demasiado grande (máx ~40MB)")
                    val url = authRepository.uploadPublicImage(
                        "stories",
                        "story_${System.currentTimeMillis()}_$index.${if (isVideo) "mp4" else "jpg"}",
                        bytes,
                        mime
                    ).getOrThrow()
                    val cap = buildString {
                        if (audience == "friends") append("[friends] ")
                        if (selected.size > 1) append("[slide ${index + 1}/${selected.size}] ")
                        append(caption)
                        storyTexts.filter { !it.isGif }.forEach { append("\n${it.text}") }
                        append(musicMeta)
                    }.trim().ifBlank { null }
                    social.publishStory(url, if (isVideo) "video" else "image", cap).getOrThrow()
                }
            }
        }.onSuccess {
            message = "Historia publicada"
            onPublished()
        }.onFailure {
            message = it.message ?: "Error al publicar"
        }
        busy = false
        showPublishSheet = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (phase) {
            "gallery" -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text("Nueva historia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { phase = "music" }) {
                            Icon(Icons.Default.MusicNote, null, tint = Color(0xFF3797F0), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Música", color = Color(0xFF3797F0))
                        }
                    }
                    if (!hasPermission) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Necesitamos acceso a tu galería", color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { ensurePermission() }) { Text("Dar permiso") }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            gridItems(gallery, key = { it.uri.toString() }) { item ->
                                val isSel = selected.contains(item.uri)
                                Box(
                                    Modifier
                                        .aspectRatio(1f)
                                        .clickable { toggleSelect(item.uri) }
                                ) {
                                    AsyncImage(
                                        item.uri, null,
                                        Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSel) {
                                        Box(
                                            Modifier.fillMaxSize().background(Color(0xFF3797F0).copy(0.35f)),
                                            contentAlignment = Alignment.TopEnd
                                        ) {
                                            Text(
                                                "${selected.indexOf(item.uri) + 1}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .background(Color(0xFF3797F0), CircleShape)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (selected.isNotEmpty()) {
                            Button(
                                onClick = { phase = "editor" },
                                modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797F0)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Siguiente (${selected.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "music" -> {
                Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { phase = "gallery" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text("Música para la historia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    OutlinedTextField(
                        value = musicQuery,
                        onValueChange = { musicQuery = it },
                        placeholder = { Text("Buscar canción o artista…", color = Color.White.copy(.4f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3797F0),
                            unfocusedBorderColor = Color.White.copy(.2f)
                        )
                    )
                    selectedStorySong?.let { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1C2A3A))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                song.coverUrl, null,
                                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Seleccionada", color = Color(0xFF3797F0), fontSize = 11.sp)
                                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(song.artist, color = Color.White.copy(.6f), fontSize = 12.sp, maxLines = 1)
                            }
                            TextButton(onClick = { selectedStorySong = null; attachMusic = false }) {
                                Text("Quitar", color = Color(0xFFFF8A80))
                            }
                        }
                    }
                    if (musicLoading) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(musicResults, key = { it.id }) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedStorySong = song
                                        attachMusic = true
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    song.coverUrl, null,
                                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222222)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, color = Color.White, maxLines = 1)
                                    Text(song.artist, color = Color.White.copy(.55f), fontSize = 13.sp, maxLines = 1)
                                }
                                if (selectedStorySong?.id == song.id) {
                                    Text("✓", color = Color(0xFF3797F0), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                musicOnly = true
                                attachMusic = true
                                phase = "editor"
                            },
                            enabled = selectedStorySong != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Solo música", color = Color.White) }
                        Button(
                            onClick = {
                                attachMusic = selectedStorySong != null
                                phase = if (selected.isEmpty()) "gallery" else "editor"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797F0))
                        ) { Text("Usar", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            else -> {
                // EDITOR
                val previewUri = selected.firstOrNull()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (previewUri != null) {
                        AsyncImage(previewUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (musicOnly && selectedStorySong != null) {
                        val ms = selectedStorySong!!
                        Box(Modifier.fillMaxSize().background(Color(0xFF121218)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    ms.coverUrl, null,
                                    Modifier
                                        .size(200.dp)
                                        .clip(CircleShape)
                                        .border(6.dp, Color.White.copy(.15f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(ms.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(ms.artist, color = Color.White.copy(.7f))
                            }
                        }
                    }

                    // overlays
                    storyTexts.forEachIndexed { idx, st ->
                        key(st.gifUrl ?: st.text, idx) {
                            var offset by remember(st) { mutableStateOf(st.offset) }
                            var dragY by remember { mutableStateOf(0f) }
                            val alpha by animateFloatAsState(if (dragY > 180f) 0.35f else 1f, label = "delA")
                            Box(
                                Modifier
                                    .offset { IntOffset(offset.x.roundToInt(), (offset.y + dragY).roundToInt()) }
                                    .graphicsLayer { this.alpha = alpha }
                                    .pointerInput(idx) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                if (dragY > 220f) {
                                                    storyTexts = storyTexts.filterIndexed { i, _ -> i != idx }
                                                }
                                                dragY = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                offset = Offset(offset.x + amount.x, offset.y + amount.y)
                                                dragY += amount.y
                                                storyTexts = storyTexts.mapIndexed { i, s ->
                                                    if (i == idx) s.copy(offset = offset) else s
                                                }
                                            }
                                        )
                                    }
                            ) {
                                if (st.isGif && !st.gifUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        st.gifUrl, "GIF",
                                        Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        st.text,
                                        color = Color.White,
                                        fontSize = if (st.style == 0) 28.sp else 22.sp,
                                        fontWeight = if (st.style == 0) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier
                                            .background(Color.Black.copy(0.35f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            phase = if (selected.isEmpty() && musicOnly) "music" else "gallery"
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { showPublishSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797F0)),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Listo", fontWeight = FontWeight.Bold) }
                    }

                    // side tools
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolFab(Icons.Default.TextFields) { showTextEditor = true }
                        ToolFab(Icons.Default.EmojiEmotions) { stickerOpen = true }
                        ToolFab(Icons.Default.MusicNote) { phase = "music" }
                        ToolFab(Icons.Default.Collections) { phase = "gallery" }
                    }

                    selectedStorySong?.let { song ->
                        if (attachMusic) {
                            Row(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(0.55f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${song.title} · ${song.artist}", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                if (showTextEditor) {
                    ModalBottomSheet(
                        onDismissRequest = { showTextEditor = false },
                        containerColor = Color(0xFF1C1C1E)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Texto", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { draftText = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Clásico", "Moderno", "Firma").forEachIndexed { i, label ->
                                    FilterChip(
                                        selected = textStyle == i,
                                        onClick = { textStyle = i },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (draftText.isNotBlank()) {
                                        storyTexts = storyTexts + StoryText(draftText.trim(), Offset(80f, 320f), textStyle)
                                        draftText = ""
                                        showTextEditor = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797F0))
                            ) { Text("Agregar") }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }

                if (stickerOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { stickerOpen = false },
                        containerColor = Color(0xFF1C1C1E)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("GIFs", color = Color.White, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = giphyQuery,
                                onValueChange = { giphyQuery = it },
                                placeholder = { Text("Buscar Giphy…", color = Color.White.copy(.4f)) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3797F0),
                                    unfocusedBorderColor = Color.White.copy(.2f)
                                )
                            )
                            if (giphyLoading) {
                                Text("Cargando GIFs…", color = Color.White.copy(.5f))
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxWidth().height(260.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    gridItems(giphyResults, key = { it.id }) { g ->
                                        AsyncImage(
                                            g.previewUrl, null,
                                            Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    storyTexts = storyTexts + StoryText(
                                                        text = "GIF",
                                                        offset = Offset(40f, 200f),
                                                        isGif = true,
                                                        gifUrl = g.fullUrl
                                                    )
                                                    stickerOpen = false
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                if (showPublishSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showPublishSheet = false },
                        containerColor = Color(0xFF1C1C1E)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Compartir historia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { scope.launch { publish("public") } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797F0))
                            ) {
                                Text(if (busy) "Publicando…" else "Tu historia (pública)", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { scope.launch { publish("friends") } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Mejores amigos", color = Color.White) }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }

        message?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg,
                    color = if (msg.startsWith("Historia")) Color(0xFF9BE7B0) else Color(0xFFFF8A80),
                    modifier = Modifier
                        .padding(24.dp)
                        .background(Color.Black.copy(.7f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolFab(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun loadGallery(context: android.content.Context): List<GalleryItem> {
    val out = mutableListOf<GalleryItem>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )
    val selection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        while (c.moveToNext() && out.size < 200) {
            val id = c.getLong(idCol)
            val type = c.getInt(typeCol)
            val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
            out.add(GalleryItem(uri, isVideo))
        }
    }
    return out
}
