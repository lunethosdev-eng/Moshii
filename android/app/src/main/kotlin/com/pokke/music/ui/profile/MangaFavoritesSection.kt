package com.pokke.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.remote.MangaDexApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class MangaFav(val id: String, val title: String, val cover: String?)

fun parseMangaFavs(json: String?): List<MangaFav> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MangaFav(
                id = o.optString("id"),
                title = o.optString("title"),
                cover = o.optString("cover").takeIf { it.isNotBlank() && it != "null" }
            ).takeIf { it.id.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

fun mangaFavsToJson(list: List<MangaFav>): String {
    val arr = JSONArray()
    list.forEach { m ->
        arr.put(JSONObject().apply {
            put("id", m.id)
            put("title", m.title)
            put("cover", m.cover ?: JSONObject.NULL)
        })
    }
    return arr.toString()
}

@Composable
fun MangaFavoritesDisplay(
    json: String?,
    accent: Color,
    ink: Color,
    muted: Color,
    cardBg: Color
) {
    val list = remember(json) { parseMangaFavs(json) }
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("Mangas favoritos", color = muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            list.forEach { m ->
                Column(
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(80.dp, 110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1D2A))
                    ) {
                        if (!m.cover.isNullOrBlank()) {
                            AsyncImage(m.cover, m.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.title,
                        color = ink,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

/** Editor: search MangaDex + add/remove favorites (max 12). */
@Composable
fun MangaFavoritesEditor(
    valueJson: String,
    onChange: (String) -> Unit,
    accent: Color,
    ink: Color,
    muted: Color
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MangaDexApi.MangaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember(valueJson) { parseMangaFavs(valueJson).toMutableStateList() }

    fun commit() = onChange(mangaFavsToJson(selected.toList()))

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList(); error = null; return@LaunchedEffect
        }
        delay(400)
        loading = true
        error = null
        MangaDexApi.searchManga(query, 12)
            .onSuccess { results = it }
            .onFailure { error = it.localizedMessage; results = emptyList() }
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Mangas favoritos (MangaDex)", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))

        // Selected chips
        if (selected.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { m ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!m.cover.isNullOrBlank()) {
                            AsyncImage(
                                m.cover, null,
                                Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(m.title, color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Close, "Quitar",
                            tint = muted,
                            modifier = Modifier.size(14.dp).clickable {
                                selected.removeAll { it.id == m.id }
                                commit()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Search field
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.06f))
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Buscar manga en MangaDex…", color = muted, fontSize = 14.sp)
                    inner()
                }
            )
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
        }

        if (error != null) {
            Text(error!!, color = Color(0xFFFF9C9C), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }

        results.take(8).forEach { item ->
            val already = selected.any { it.id == item.id }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !already && selected.size < 12) {
                        selected.add(MangaFav(item.id, item.title, item.coverUrl))
                        commit()
                        query = ""
                        results = emptyList()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp, 50.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1A1D2A))
                ) {
                    if (!item.coverUrl.isNullOrBlank()) {
                        AsyncImage(item.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.year?.toString(), item.status).joinToString(" · "),
                        color = muted,
                        fontSize = 11.sp
                    )
                }
                if (already) Text("Añadido", color = accent, fontSize = 11.sp)
                else Icon(Icons.Default.Add, "Añadir", tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        if (selected.size >= 12) {
            Text("Máximo 12 mangas en el perfil", color = muted, fontSize = 11.sp)
        }
    }
}
