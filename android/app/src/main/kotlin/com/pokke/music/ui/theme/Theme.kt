package com.pokke.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pokke.music.data.preferences.UserPreferences

private fun pokkeDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    secondary = accent.copy(alpha = 0.85f),
    onSecondary = Color.Black,
    tertiary = accent.copy(alpha = 0.65f),
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = Color(0xFFB0B0B8),
    outline = Color.White.copy(alpha = 0.18f),
)

private fun pokkeLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = accent.copy(alpha = 0.85f),
    onSecondary = Color.White,
    tertiary = accent.copy(alpha = 0.75f),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF17171D),
    surface = Color.White,
    onSurface = Color(0xFF17171D),
    surfaceVariant = Color(0xFFE9E9F0),
    onSurfaceVariant = Color(0xFF555560),
    outline = Color(0xFF777783).copy(alpha = 0.35f),
)

@Composable
fun PokkeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = UserPreferences(context)
    val accentKey by prefs.accentColor.collectAsState("violet")
    val themeKey by prefs.appTheme.collectAsState("dark")
    val useDynamicColors by prefs.dynamicColors.collectAsState(true)
    val resolvedDark = when (themeKey.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }
    val accent = accentColor(accentKey)
    val scheme = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (resolvedDark) {
        pokkeDarkScheme(accent)
    } else {
        pokkeLightScheme(accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
