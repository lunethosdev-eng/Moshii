package com.pokke.music.ui.theme

import androidx.compose.ui.graphics.Color

/** App background used across screens (Library, Search, etc.). */
val PokkeBlack = Color(0xFF0B0B0E)

val PokkeViolet = Color(0xFF8B5CF6)
val PokkeOcean = Color(0xFF38BDF8)
val PokkeRose = Color(0xFFF472B6)
val PokkeForest = Color(0xFF34D399)
val PokkeMono = Color(0xFFE5E5EA)

val BackgroundDark = PokkeBlack
val SurfaceDark = Color(0xFF16161C)
val SurfaceElevated = Color(0xFF1E1E26)

fun accentColor(theme: String): Color = when (theme.lowercase()) {
    "ocean", "blue" -> PokkeOcean
    "rose", "pink" -> PokkeRose
    "forest", "green" -> PokkeForest
    "mono", "white" -> PokkeMono
    else -> PokkeViolet
}
