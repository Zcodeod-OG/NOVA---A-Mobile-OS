package com.nova.runtime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS-inspired system palette
val NovaSystemBlue = Color(0xFF007AFF)
val NovaSystemGreen = Color(0xFF34C759)
val NovaSystemRed = Color(0xFFFF3B30)
val NovaSystemOrange = Color(0xFFFF9500)
val NovaSystemGray = Color(0xFF8E8E93)

// Legacy aliases (used across components)
val NovaCyanAccent = NovaSystemBlue
val NovaIndigoAccent = Color(0xFF5856D6)
val NovaEmeraldGreen = NovaSystemGreen

private val NovaLightBackground = Color(0xFFF2F2F7)
private val NovaLightSurface = Color(0xFFFFFFFF)
private val NovaLightSurfaceElevated = Color(0xFFE5E5EA)
private val NovaLightTextPrimary = Color(0xFF000000)
private val NovaLightTextSecondary = Color(0xFF8E8E93)

private val DarkBackground = Color(0xFF000000)
private val DarkSurface = Color(0xFF1C1C1E)
private val DarkSurfaceElevated = Color(0xFF2C2C2E)
private val DarkSurfaceVariant = Color(0xFF3A3A3C)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFF8E8E93)

val NovaDarkBackground = DarkBackground
val NovaSurfaceDark = DarkSurface
val NovaSurfaceVariant = DarkSurfaceVariant
val NovaTextPrimary = DarkTextPrimary
val NovaTextSecondary = DarkTextSecondary

object NovaColors {
    val background @Composable get() = MaterialTheme.colorScheme.background
    val surface @Composable get() = MaterialTheme.colorScheme.surface
    val surfaceElevated @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary @Composable get() = MaterialTheme.colorScheme.onBackground
    val textSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent @Composable get() = MaterialTheme.colorScheme.primary
    val success @Composable get() = NovaSystemGreen
    val error @Composable get() = NovaSystemRed
    val glassSurface @Composable get() =
        MaterialTheme.colorScheme.surface.copy(alpha = if (isSystemInDarkTheme()) 0.72f else 0.88f)
    val glassBorder @Composable get() =
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.12f else 0.08f)
}

private val NovaLightColorScheme = lightColorScheme(
    primary = NovaSystemBlue,
    onPrimary = Color.White,
    secondary = NovaIndigoAccent,
    onSecondary = Color.White,
    tertiary = NovaSystemGreen,
    background = NovaLightBackground,
    onBackground = NovaLightTextPrimary,
    surface = NovaLightSurface,
    onSurface = NovaLightTextPrimary,
    surfaceVariant = NovaLightSurfaceElevated,
    onSurfaceVariant = NovaLightTextSecondary,
    error = NovaSystemRed,
    onError = Color.White,
)

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaSystemBlue,
    onPrimary = Color.White,
    secondary = NovaIndigoAccent,
    onSecondary = Color.White,
    tertiary = NovaSystemGreen,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    error = NovaSystemRed,
    onError = Color.White,
)

private val NovaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 41.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = (-0.1).sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        lineHeight = 13.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 18.sp,
    ),
)

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NovaDarkColorScheme else NovaLightColorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content,
    )
}
