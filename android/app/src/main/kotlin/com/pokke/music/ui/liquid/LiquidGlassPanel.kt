package com.pokke.music.ui.liquid

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Panel glass de Pokke — ahora delega al estilo Moshii (estable, sin lens).
 * API compatible: callers existentes pueden seguir usando LiquidGlassPanel.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    MoshiiGlass(
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        opacity = 0.14f,
        content = content
    )
}
