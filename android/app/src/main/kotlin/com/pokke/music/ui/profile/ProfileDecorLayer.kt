package com.pokke.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Floating profile decorations: Giphy GIFs or gallery stickers (png/webp/gif).
 * Positions stored as normalized 0..1 in profiles.profile_gifs JSON:
 * [{"id":"...","url":"...","x":0.2,"y":0.4,"w":0.18}]
 */
data class ProfileDecor(
    val id: String,
    val url: String,
    val x: Float = 0.5f,
    val y: Float = 0.3f,
    val w: Float = 0.2f
)

fun parseProfileDecors(json: String?): List<ProfileDecor> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ProfileDecor(
                id = o.optString("id", i.toString()),
                url = o.optString("url").ifBlank { return@mapNotNull null },
                x = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                y = o.optDouble("y", 0.3).toFloat().coerceIn(0f, 1f),
                w = o.optDouble("w", 0.2).toFloat().coerceIn(0.08f, 0.45f)
            )
        }
    }.getOrDefault(emptyList())
}

fun serializeProfileDecors(items: List<ProfileDecor>): String {
    val arr = JSONArray()
    items.forEach {
        arr.put(
            JSONObject()
                .put("id", it.id)
                .put("url", it.url)
                .put("x", it.x.toDouble())
                .put("y", it.y.toDouble())
                .put("w", it.w.toDouble())
        )
    }
    return arr.toString()
}

@Composable
fun ProfileGradientBackground(
    primaryHex: String?,
    secondaryHex: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val p = parseHex(primaryHex) ?: Color(0xFFAAA0FF)
    val s = parseHex(secondaryHex) ?: Color(0xFFFF9FCB)
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(p.copy(alpha = 0.35f), s.copy(alpha = 0.18f), Color.Transparent)
                )
            )
    )
}

fun parseHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val h = hex.removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> Color(android.graphics.Color.parseColor("#$h"))
            8 -> Color(android.graphics.Color.parseColor("#$h"))
            else -> null
        }
    }.getOrNull()
}

@Composable
fun ProfileDecorLayer(
    items: List<ProfileDecor>,
    editable: Boolean,
    onMoved: (List<ProfileDecor>) -> Unit,
    modifier: Modifier = Modifier
) {
    var local by remember(items) { mutableStateOf(items) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        local.forEachIndexed { index, decor ->
            val sizePx = (maxW * decor.w).coerceIn(48f, maxW * 0.45f)
            var offset by remember(decor.id) {
                mutableStateOf(
                    Offset(decor.x * maxW - sizePx / 2f, decor.y * maxH - sizePx / 2f)
                )
            }
            Box(
                Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .size(with(density) { sizePx.toDp() })
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (editable) Modifier.pointerInput(decor.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                offset = Offset(
                                    (offset.x + drag.x).coerceIn(0f, maxW - sizePx),
                                    (offset.y + drag.y).coerceIn(0f, maxH - sizePx)
                                )
                                val nx = ((offset.x + sizePx / 2f) / maxW).coerceIn(0f, 1f)
                                val ny = ((offset.y + sizePx / 2f) / maxH).coerceIn(0f, 1f)
                                local = local.toMutableList().also {
                                    it[index] = decor.copy(x = nx, y = ny)
                                }
                                onMoved(local)
                            }
                        } else Modifier
                    )
            ) {
                AsyncImage(
                    model = decor.url,
                    contentDescription = "Decor",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

