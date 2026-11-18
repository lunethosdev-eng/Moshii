package com.pokke.music.ui.sheets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pokke.music.data.preferences.UserPreferences

@Composable
fun OverflowMenuSheet(
    anchorRect: Rect? = null,
    onDismiss: () -> Unit,
    onShareSong: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSleepTimer: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onSongInfo: () -> Unit,
    glass: Boolean = true
) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferences(context.applicationContext) }
    // The menu must use the real app toggle, never infer Liquid Glass from the call-site default.
    val liquidGlassEnabled by preferences.liquidGlassEnabled.collectAsState(initial = false)
    val useLiquidMenu = liquidGlassEnabled && glass
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scaleX by animateFloatAsState(
        targetValue = if (visible) 1f else if (useLiquidMenu) .72f else .94f,
        animationSpec = tween(if (useLiquidMenu) 360 else 170), label = "menuScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (visible) 1f else if (useLiquidMenu) .30f else .94f,
        animationSpec = tween(if (useLiquidMenu) 390 else 170), label = "menuScaleY"
    )
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (useLiquidMenu) 220 else 130), label = "menuAlpha")
    val radius by animateDpAsState(if (visible && useLiquidMenu) 22.dp else 18.dp, tween(330), label = "menuRadius")
    val shape = RoundedCornerShape(topStart = radius, topEnd = 12.dp, bottomStart = radius, bottomEnd = radius)

    val positionProvider = remember(anchorRect) { AnchorAwareMenuPositionProvider(anchorRect) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            Modifier.width(286.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    transformOrigin = TransformOrigin(0.92f, 0.02f)
                    translationY = if (useLiquidMenu) (1f - scaleY) * -10f else 0f
                }
                .shadow(if (useLiquidMenu) 28.dp else 16.dp, shape, clip = false)
                .clip(shape)
                .then(
                    if (useLiquidMenu) Modifier
                        .background(Brush.linearGradient(listOf(Color(0xF03B3B49), Color(0xE91D1D28), Color(0xF02D2939))))
                        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.36f), Color.White.copy(.08f), Color.White.copy(.22f))), shape)
                    else Modifier
                        .background(Color(0xF51D1D23))
                        .border(.6.dp, Color.White.copy(.12f), shape)
                )
                .padding(vertical = 8.dp)
        ) {
            Column {
                MenuItem(Icons.Default.Share, "Compartir canción", useLiquidMenu, onShareSong)
                MenuItem(Icons.Default.Favorite, "Agregar / quitar de favoritos", useLiquidMenu, onFavorite)
                MenuItem(Icons.Default.Download, "Descargar para offline", useLiquidMenu, onDownload)
                HorizontalDivider(color = Color.White.copy(if (useLiquidMenu) .18f else .10f), modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp))
                MenuItem(Icons.Default.Queue, "Reproducir después", useLiquidMenu, onPlayNext)
                MenuItem(Icons.Default.Queue, "Agregar a la cola", useLiquidMenu, onAddToQueue)
                MenuItem(Icons.Default.Queue, "Agregar a una playlist", useLiquidMenu, onAddToPlaylist)
                MenuItem(Icons.Default.Delete, "Quitar de la cola", useLiquidMenu, onRemoveFromQueue)
                HorizontalDivider(color = Color.White.copy(if (useLiquidMenu) .18f else .10f), modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp))
                MenuItem(Icons.Default.Timer, "Temporizador de apagado", useLiquidMenu, onSleepTimer)
                MenuItem(Icons.Default.Info, "Información de la canción", useLiquidMenu, onSongInfo)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, liquid: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (liquid) Color.White.copy(alpha = .001f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = if (liquid) 12.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = .94f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}


/** Positions the popup from the actual overflow button bounds, clamped to the window. */
private class AnchorAwareMenuPositionProvider(
    private val anchor: Rect?,
    private val gapPx: Int = 4
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = 12
        val target = anchor ?: Rect(
            (windowSize.width - margin).toFloat(),
            72f,
            (windowSize.width - margin).toFloat(),
            72f
        )
        val rightAligned = target.right.toInt() - popupContentSize.width
        val x = rightAligned.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = if (anchor == null) 96 else target.bottom.toInt() + gapPx
        val above = target.top.toInt() - popupContentSize.height - gapPx
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below else above
        return IntOffset(x, y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
    }
}
