package com.pokke.music.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.data.remote.MusicSource
import com.pokke.music.data.remote.MusicSourcesConfig
import com.pokke.music.data.repository.AuthRepository
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0E0F14)
private val Card = Color(0xFF1A1C26)
private val Ink = Color(0xFFF3F2FA)
private val Muted = Color(0xFFA5A4B5)
private val Accent = Color(0xFFE5C17A)

/**
 * Owner-only panel. PIN is checked only for the owner email account.
 * Categories: User server | Music servers | Emblems
 */
@Composable
fun AdminPanelScreen(
    repository: AuthRepository,
    ownerEmail: String = "lunethos.dev@gmail.com",
    onBack: () -> Unit
) {
    val session by repository.session.collectAsState()
    val scope = rememberCoroutineScope()
    var pinInput by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    // Emblem grant
    var targetUser by remember { mutableStateOf("") }
    var emblemId by remember { mutableStateOf("pro") }

    // Add music source form
    var srcName by remember { mutableStateOf("") }
    var srcLabel by remember { mutableStateOf("") }
    var srcUrl by remember { mutableStateOf("") }
    var srcKey by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf(MusicSourcesConfig.enabled()) }

    val isOwnerAccount = session?.email?.equals(ownerEmail, ignoreCase = true) == true

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Ink)
            }
            Text("Admin Panel", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (!isOwnerAccount) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Solo la cuenta Owner puede abrir este panel.", color = Muted, fontSize = 14.sp)
                    Text("Inicia sesión con la cuenta owner.", color = Muted, fontSize = 12.sp)
                }
            }
            return
        }

        if (!unlocked) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("PIN de Owner", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(12) },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(0.2f)
                    )
                )
                if (pinError != null) Text(pinError!!, color = Color(0xFFFF9C9C), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        // PIN verified only on owner account; compare against config (hash in production)
                        if (pinInput == "4049959") {
                            unlocked = true
                            pinError = null
                        } else pinError = "PIN incorrecto"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
                ) { Text("Desbloquear", fontWeight = FontWeight.Bold) }
            }
            return
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Usuarios", "Música", "Emblemas").forEachIndexed { i, label ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            when (i) {
                                0 -> Icons.Default.People
                                1 -> Icons.Default.Cloud
                                else -> Icons.Default.Badge
                            }, null, Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            when (tab) {
                0 -> {
                    Text("Servidor de usuarios (Pokke Users)", color = Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    InfoCard("Proyecto", "llkatqhbganntyhppchl · https://llkatqhbganntyhppchl.supabase.co")
                    InfoCard("Auth", "Perfiles, favoritos, playlists, emblemas, mensajes")
                    InfoCard("Owner", ownerEmail)
                }
                1 -> {
                    Text("Servidores de música", color = Accent, fontWeight = FontWeight.Bold)
                    Text("La app usa TODAS las fuentes activas a la vez (búsqueda fusionada).", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    sources.forEach { s ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) {
                            Column {
                                Text(s.label, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(s.supabaseUrl, color = Muted, fontSize = 11.sp)
                                Text(if (s.isPrimary) "Principal" else "Secundaria", color = Accent, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Agregar servidor de música", color = Ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(srcName, { srcName = it }, label = { Text("id (slug)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
                        colors = fieldColors())
                    OutlinedTextField(srcLabel, { srcLabel = it }, label = { Text("Nombre visible") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
                        colors = fieldColors())
                    OutlinedTextField(srcUrl, { srcUrl = it }, label = { Text("Supabase URL") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
                        colors = fieldColors())
                    OutlinedTextField(srcKey, { srcKey = it }, label = { Text("Publishable key") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
                        colors = fieldColors())
                    Button(
                        onClick = {
                            if (srcName.isBlank() || srcUrl.isBlank()) {
                                message = "Completa id y URL"
                                return@Button
                            }
                            val neu = MusicSource(srcName, srcName, srcLabel.ifBlank { srcName }, srcUrl.trim(), srcKey.trim(), true, false)
                            val next = MusicSourcesConfig.runtime + neu
                            MusicSourcesConfig.runtime = next
                            sources = next.filter { it.isEnabled }
                            message = "Fuente '${neu.label}' agregada en runtime. Guárdala también en music_sources del dashboard."
                            srcName = ""; srcLabel = ""; srcUrl = ""; srcKey = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Agregar fuente", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Text("Otorgar emblemas", color = Accent, fontWeight = FontWeight.Bold)
                    Text("Por username o user id UUID", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(targetUser, { targetUser = it }, label = { Text("@username o UUID") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, colors = fieldColors())
                    OutlinedTextField(emblemId, { emblemId = it }, label = { Text("emblem id (pro, artist, owner, early, verified, new_member)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, colors = fieldColors())
                    Button(
                        onClick = {
                            scope.launch {
                                repository.grantEmblem(targetUser.trim(), emblemId.trim())
                                    .onSuccess { message = "Emblema '$emblemId' otorgado a $targetUser" }
                                    .onFailure { message = it.message ?: "Error al otorgar" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
                    ) { Text("Otorgar emblema", fontWeight = FontWeight.Bold) }
                }
            }
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(message!!, color = if (message!!.startsWith("Error")) Color(0xFFFF9C9C) else Color(0xFF8FE0B0), fontSize = 13.sp)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) {
        Text(title, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Ink, fontSize = 13.sp)
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink, unfocusedTextColor = Ink,
    focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(0.2f),
    focusedLabelColor = Accent, unfocusedLabelColor = Muted
)
