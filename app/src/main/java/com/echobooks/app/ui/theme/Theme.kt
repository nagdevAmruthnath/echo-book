package com.echobooks.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0A1F)
val InkDeep = Color(0xFF070615)
val SurfaceGlass = Color(0xFF1B1540)
val Violet = Color(0xFF8B7CFF)
val Cyan = Color(0xFF4DD6FF)
val Magenta = Color(0xFFFF5FA2)
val Mint = Color(0xFF4DFFB0)
val Amber = Color(0xFFFFC24D)
val TextPrimary = Color(0xFFF2F1FF)
val TextSecondary = Color(0xFFB9B3E6)
val ErrorRed = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2E7A),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Cyan,
    onSecondary = Color(0xFF062B3A),
    tertiary = Magenta,
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceGlass,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF2A2355),
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun EchoBooksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}