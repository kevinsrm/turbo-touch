package com.kevinsrm.turbotouch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF2FB6C4)
private val AccentDark = Color(0xFF0E6C76)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00272C),
    secondary = Color(0xFF9BD6DE),
    background = Color(0xFF0B0F12),
    surface = Color(0xFF12181C),
    surfaceVariant = Color(0xFF1C252B),
    onSurface = Color(0xFFE3E8EA)
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = Color(0xFF3E6F77),
    background = Color(0xFFF5F8F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE1E9EC),
    onSurface = Color(0xFF101619)
)

@Composable
fun TurboTouchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
