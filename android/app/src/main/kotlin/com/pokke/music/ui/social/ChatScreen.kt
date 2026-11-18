package com.pokke.music.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.SocialRepository
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    authRepository: AuthRepository,
    onBack: (() -> Unit)? = null,
    onOpenMaps: (() -> Unit)? = null,
    onOpenStory: (() -> Unit)? = null
) {
    val social = remember(authRepository) { SocialRepository(authRepository) }
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<SocialRepository.Friend>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<SocialRepository.Conversation>>(emptyList()) }
    var selected by remember { mutableStateOf<SocialRepository.Conversation?>(null) }
    var messages by remember { mutableStateOf<List<SocialRepository.Message>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val myId = authRepository.session.collectAsState().value?.userId

    fun refresh() {
        scope.launch {
            loading = true
            social.friends().onSuccess { friends = it }.onFailure { error = it.message }
            social.conversations().onSuccess { conversations = it }.onFailure { error = it.message }
            selected?.let { convo -> social.messages(convo.id).onSuccess { messages = it }.onFailure { error = it.message } }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().background(Color(0xFF09090B)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) IconButton(onClick = { selected = null; messages = emptyList() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(selected?.friend?.displayName ?: "Chats", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (selected == null) "Solo puedes conversar con amigos aceptados" else "Chat privado · Pokke Alfa 1.0", color = Color.White.copy(.5f), fontSize = 12.sp)
            }
            if (selected == null && onOpenMaps != null) {
                TextButton(onClick = onOpenMaps) { Text("Mapa", color = Color(0xFFE5C17A)) }
            }
            if (selected == null && onOpenStory != null) {
                TextButton(onClick = onOpenStory) { Text("Historia", color = Color(0xFFE5C17A)) }
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White) }
        }
        error?.let { Text(it, color = Color(0xFFFF8B8B), modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp), fontSize = 12.sp) }

        if (selected == null) {
            Text("CONVERSACIONES", color = Color(0xFFE5C17A), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { convo ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = convo
                        scope.launch {
                            social.messages(convo.id).onSuccess { messages = it }.onFailure { error = it.message }
                        }
                    }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF29252A)), contentAlignment = Alignment.Center) { Text(convo.friend.displayName.take(1).uppercase(), color = Color(0xFFE5C17A), fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(convo.friend.displayName.ifBlank { convo.friend.username }, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(convo.lastMessage ?: "Inicia una conversación", color = Color.White.copy(.48f), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                item { Text("AMIGOS", color = Color(0xFFE5C17A), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp)) }
                items(friends, key = { it.id }) { friend ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF29252A)), contentAlignment = Alignment.Center) { Text(friend.displayName.take(1).uppercase(), color = Color(0xFFE5C17A)) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(friend.displayName.ifBlank { friend.username }, color = Color.White)
                            Text("@${friend.username}", color = Color.White.copy(.45f), fontSize = 11.sp)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                loading = true
                                social.openConversation(friend.id).onSuccess { id ->
                                    selected = SocialRepository.Conversation(id, friend, null, null)
                                    social.messages(id).onSuccess { messages = it }.onFailure { error = it.message }
                                }.onFailure { error = it.message }
                                loading = false
                            }
                        }) { Text("Mensaje", color = Color(0xFFE5C17A)) }
                    }
                }
                if (friends.isEmpty() && conversations.isEmpty()) item { Text("Todavía no tienes amigos aceptados. Cuando ambos acepten la solicitud, aparecerán aquí.", color = Color.White.copy(.5f), modifier = Modifier.padding(20.dp)) }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), reverseLayout = false) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderId == myId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.padding(vertical = 4.dp).widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) Color(0xFFB98C48) else Color(0xFF252329)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(message.body ?: if (message.type == "image") "Imagen adjunta" else "Archivo adjunto", color = if (mine) Color.Black else Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Escribe un mensaje…") }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFE5C17A), unfocusedBorderColor = Color(0xFF49454D)))
                IconButton(enabled = draft.isNotBlank() && !loading, onClick = {
                    val text = draft
                    draft = ""
                    scope.launch {
                        loading = true
                        social.sendText(selected!!.id, text).onSuccess { social.messages(selected!!.id).onSuccess { messages = it } }.onFailure { error = it.message; draft = text }
                        loading = false
                    }
                }) { Icon(Icons.Filled.Send, "Enviar", tint = Color(0xFFE5C17A)) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFE5C17A), trackColor = Color.Transparent)
    }
}
