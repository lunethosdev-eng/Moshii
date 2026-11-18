package com.pokke.music.ui.liquid

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass inspirado en Moshii (Flutter BackdropFilter + specular).
 * Versión Compose estable: sin lens/chromatic (evita crashes A71 / RenderThread).
 * - enabled=false → panel opaco
 * - enabled=true  → relleno translúcido, borde luminoso, highlight superior
 */
@Composable
fun MoshiiGlass(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    blurHint: Dp = 24.dp, // reserved for future backdrop host; visual weight only
    opacity: Float = 0.14f,
    tint: Color = Color.White,
    borderWidth: Dp = 0.8.dp,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val radiusShape = shape
    if (!enabled) {
        Box(
            modifier
                .clip(radiusShape)
                .background(Color(0xFF1A1A1E).copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), radiusShape)
                .then(if (elevation > 0.dp) Modifier.shadow(elevation, radiusShape) else Modifier),
            content = content
        )
        return
    }

    val op = opacity.coerceIn(0.02f, 0.5f)
    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, radiusShape, ambientColor = Color.Black.copy(0.25f)) else Modifier)
            .clip(radiusShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = (op * 1.35f).coerceAtMost(0.55f)),
                        tint.copy(alpha = op * 0.55f),
                        tint.copy(alpha = (op * 0.95f).coerceAtMost(0.45f))
                    )
                )
            )
            .background(Color(0xFF121218).copy(alpha = 0.45f))
            .border(borderWidth, Color.White.copy(alpha = 0.22f), radiusShape)
    ) {
        // Specular highlight (top edge)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.08f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
        )
        content()
    }
}

/** Modifier helper for nav pills / chips matching Moshii glass fill. */
fun Modifier.moshiiGlassFill(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(40.dp),
    opacity: Float = 0.14f
): Modifier {
    val op = if (enabled) opacity.coerceIn(0.02f, 0.5f) else 0.92f
    return this
        .clip(shape)
        .background(
            if (enabled) Color.White.copy(alpha = op)
            else Color(0xFF1A1A1E).copy(alpha = 0.92f)
        )
        .border(
            0.8.dp,
            if (enabled) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            shape
        )
}
