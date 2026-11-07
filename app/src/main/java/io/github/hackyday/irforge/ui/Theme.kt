package io.github.hackyday.irforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import io.github.hackyday.irforge.data.Store

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF5252),
    onPrimary = Color.White,
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF12131A),
    surface = Color(0xFF1B1D26),
    surfaceVariant = Color(0xFF232633),
    onBackground = Color(0xFFEDEEF2),
    onSurface = Color(0xFFEDEEF2),
    onSurfaceVariant = Color(0xFFA9ADBB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    secondary = Color(0xFFF9A825),
    background = Color(0xFFF6F5F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEAE6),
    onBackground = Color(0xFF1A1B20),
    onSurface = Color(0xFF1A1B20),
    onSurfaceVariant = Color(0xFF565963),
)

@Composable
fun ForgeTheme(store: Store, content: @Composable () -> Unit) {
    val settings by store.settings.collectAsState()
    val dark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
