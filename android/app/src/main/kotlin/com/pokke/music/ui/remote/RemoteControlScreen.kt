package com.pokke.music.ui.remote

import android.content.Intent
import android.speech.RecognizerIntent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.remote.DiscoveredTv
import com.pokke.music.remote.PhoneRemoteClient
import com.pokke.music.remote.PhoneRemoteDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember(context) { PhoneRemoteDiscovery(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var devices by remember { mutableStateOf<List<DiscoveredTv>>(emptyList()) }
    var selected by remember { mutableStateOf<DiscoveredTv?>(null) }
    var code by remember { mutableStateOf("") }
    var remoteToken by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Busca una Pokke TV conectada a la misma red Wi‑Fi.") }
    var sending by remember { mutableStateOf(false) }
    val shortcutPrefs = remember(context) { context.getSharedPreferences("pokke_remote_shortcuts", android.content.Context.MODE_PRIVATE) }
    var shortcutOne by remember { mutableStateOf(shortcutPrefs.getString("one", "home") ?: "home") }
    var shortcutTwo by remember { mutableStateOf(shortcutPrefs.getString("two", "play_pause") ?: "play_pause") }
    var shortcutThree by remember { mutableStateOf(shortcutPrefs.getString("three", "search") ?: "search") }

    fun send(action: String, value: String = "") {
        val target = selected ?: return
        val token = remoteToken ?: run { message = "Conecta primero con el código de la TV."; return }
        scope.launch {
            sending = true
            val result = withContext(Dispatchers.IO) { PhoneRemoteClient.send(target, token, action, value) }
            result.onSuccess { message = "Control enviado" }.onFailure { message = it.message ?: "No se pudo conectar"; if (it.message?.contains("Código") == true || it.message?.contains("expirada") == true) { connected = false; remoteToken = null } }
            sending = false
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) { query = spoken; send("text", spoken) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Buscar en Pokke TV")
            }
            runCatching { voiceLauncher.launch(intent) }.onFailure { message = "El reconocimiento de voz no está disponible en este teléfono." }
        } else message = "Necesitas permitir el micrófono para buscar por voz."
    }

    DisposableEffect(discovery) { onDispose { discovery.stop() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B10)).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Control remoto", color = Color.White, fontSize = 23.sp)
                Text("Pokke TV · Red local", color = Color(0xFF9A9AA6), fontSize = 12.sp)
            }
            TextButton(onClick = {
                devices = emptyList(); selected = null; connected = false; remoteToken = null; scanning = true
                discovery.start(
                    onDevice = { found -> mainHandler.post { if (devices.none { it.host == found.host && it.port == found.port }) devices = devices + found } },
                    onError = { error -> mainHandler.post { message = error; scanning = false } }
                )
                scope.launch { kotlinx.coroutines.delay(7000); scanning = false; discovery.stop() }
            }) { Text(if (scanning) "Buscando…" else "Buscar TV") }
        }
        if (devices.isNotEmpty()) {
            Text("DISPOSITIVOS ENCONTRADOS", color = Color(0xFF9A9AA6), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            devices.forEach { device ->
                TextButton(onClick = { selected = device; connected = false; remoteToken = null; message = "Introduce el código mostrado en ${device.name}." }, modifier = Modifier.fillMaxWidth()) {
                    Text("${device.name}  ·  ${device.host.hostAddress}", color = if (selected == device) Color(0xFFB9D7FF) else Color.White)
                }
            }
        }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(8); connected = false; remoteToken = null }, label = { Text("Código de la TV") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                Button(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { PhoneRemoteClient.authenticate(selected!!, code) }
                        remoteToken = result.getOrNull()
                        connected = remoteToken != null
                        message = if (connected) "Control conectado a ${selected!!.name}. Sesión temporal de 10 minutos." else result.exceptionOrNull()?.message ?: "No se pudo autorizar"
                    }
                }, enabled = code.length == 8 && !sending) { Text("Conectar") }
            }
        }
        Text(message, color = Color(0xFFB5B5C0), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (connected) {
            Text("ATAJOS PERSONALIZABLES", color = Color(0xFF9A9AA6), fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShortcutPicker("Atajo 1", shortcutOne, Modifier.weight(1f), onSelect = { shortcutOne = it; shortcutPrefs.edit().putString("one", it).apply() }, onRun = { send(it) })
                ShortcutPicker("Atajo 2", shortcutTwo, Modifier.weight(1f), onSelect = { shortcutTwo = it; shortcutPrefs.edit().putString("two", it).apply() }, onRun = { send(it) })
                ShortcutPicker("Atajo 3", shortcutThree, Modifier.weight(1f), onSelect = { shortcutThree = it; shortcutPrefs.edit().putString("three", it).apply() }, onRun = { send(it) })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RemoteButton("Atrás", { send("back") }, Icons.AutoMirrored.Filled.ArrowBack)
                RemoteButton("Inicio", { send("home") }, Icons.Filled.Home)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF191A22)).border(1.dp, Color(0xFF333541), RoundedCornerShape(28.dp)).pointerInput(selected, code) {
                var dx = 0f; var dy = 0f
                detectDragGestures(onDrag = { change, drag -> change.consume(); dx += drag.x; dy += drag.y }, onDragEnd = {
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 25f) send(if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) if (dx > 0) "right" else "left" else if (dy > 0) "down" else "up")
                    dx = 0f; dy = 0f
                })
            }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Panel táctil", color = Color.White, fontSize = 18.sp)
                    Text("Desliza para navegar · toca Seleccionar", color = Color(0xFF9A9AA6), fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { send("select") }, shape = CircleShape, modifier = Modifier.size(64.dp)) { Text("OK") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteButton("↑", { send("up") }, Icons.Filled.ArrowUpward)
                RemoteButton("←", { send("left") }, Icons.AutoMirrored.Filled.ArrowBack)
                RemoteButton("↓", { send("down") }, Icons.Filled.ArrowDownward)
                RemoteButton("→", { send("right") }, Icons.Filled.ArrowForward)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteButton("Anterior", { send("previous") }, Icons.Filled.SkipPrevious)
                RemoteButton("Play/Pausa", { send("play_pause") }, Icons.Filled.PlayArrow)
                RemoteButton("Siguiente", { send("next") }, Icons.Filled.SkipNext)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteButton("Vol −", { send("volume_down") }, Icons.Filled.VolumeDown)
                RemoteButton("Silencio", { send("mute") }, Icons.Filled.VolumeMute)
                RemoteButton("Vol +", { send("volume_up") }, Icons.Filled.VolumeUp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(value = query, onValueChange = { query = it.take(120) }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1B23)).padding(14.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { send("text", query) }), decorationBox = { inner -> if (query.isEmpty()) Text("Escribir en Search…", color = Color(0xFF777783)); inner() })
                IconButton(onClick = { send("text", query) }) { Icon(Icons.Filled.Search, null, tint = Color.White) }
                IconButton(onClick = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                    Icon(Icons.Filled.Mic, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RemoteButton(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) { Icon(icon, contentDescription = label) }
        Text(label, color = Color(0xFFB8B8C3), fontSize = 10.sp)
    }
}


@Composable
private fun ShortcutPicker(
    title: String,
    action: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onRun: (String) -> Unit
) {
    val choices = listOf("home" to "Inicio", "back" to "Atrás", "search" to "Buscar", "play_pause" to "Play/Pausa", "next" to "Siguiente", "previous" to "Anterior")
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(title, color = Color(0xFF8F8F9A), fontSize = 10.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)) {
                Text(choices.firstOrNull { it.first == action }?.second ?: "Elegir", color = Color.White, fontSize = 11.sp, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelect(key); onRun(key) })
                }
            }
        }
    }
}
