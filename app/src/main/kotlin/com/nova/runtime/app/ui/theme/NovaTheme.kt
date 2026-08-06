package com.nova.runtime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NovaDarkBackground = Color(0xFF0F172A) // Slate 900
val NovaSurfaceDark = Color(0xFF1E293B) // Slate 800
val NovaSurfaceVariant = Color(0xFF334155) // Slate 700
val NovaCyanAccent = Color(0xFF06B6D4) // Cyan 500
val NovaIndigoAccent = Color(0xFF6366F1) // Indigo 500
val NovaEmeraldGreen = Color(0xFF10B981) // Emerald 500
val NovaTextPrimary = Color(0xFFF8FAFC) // Slate 50
val NovaTextSecondary = Color(0xFF94A3B8) // Slate 400

private val NovaColorScheme = darkColorScheme(
    primary = NovaCyanAccent,
    secondary = NovaIndigoAccent,
    tertiary = NovaEmeraldGreen,
    background = NovaDarkBackground,
    surface = NovaSurfaceDark,
    surfaceVariant = NovaSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = NovaTextPrimary,
    onSurface = NovaTextPrimary,
    onSurfaceVariant = NovaTextSecondary
)

@Composable
fun NovaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaColorScheme,
        content = content
    )
}
