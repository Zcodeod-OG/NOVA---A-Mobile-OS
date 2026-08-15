package com.nova.runtime.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Objective Modernist (Swiss Style) Palette Specifications
val NovaAkzidenzRed = Color(0xFFB5000B)
val NovaPrimaryRedContainer = Color(0xFFE30613)
val NovaPureBlack = Color(0xFF1A1C1C)
val NovaOffWhite = Color(0xFFF9F9F9)
val NovaSurfaceLowest = Color(0xFFFFFFFF)
val NovaSurfaceLow = Color(0xFFF3F3F3)
val NovaSurfaceContainer = Color(0xFFEEEEEE)
val NovaSurfaceHigh = Color(0xFFE8E8E8)
val NovaSurfaceHighest = Color(0xFFE2E2E2)
val NovaSurfaceContainerHighest = Color(0xFFE2E2E2)
val NovaSurfaceDim = Color(0xFFDADADA)
val NovaSecondaryGrey = Color(0xFF5E5E5E)
val NovaOnSecondaryVariant = Color(0xFF646464)
val NovaOutlineGrey = Color(0xFF936E69)
val NovaErrorRed = Color(0xFFBA1A1A)

// Legacy & UI Color Aliases for backward compatibility
val NovaNeonOrange = NovaAkzidenzRed
val NovaSystemBlue = Color(0xFF1A1C1C)
val NovaSystemGreen = NovaAkzidenzRed
val NovaSystemRed = NovaErrorRed
val NovaSystemOrange = NovaAkzidenzRed
val NovaSystemGray = NovaSecondaryGrey
val NovaCyanAccent = NovaAkzidenzRed
val NovaIndigoAccent = NovaPureBlack
val NovaEmeraldGreen = NovaAkzidenzRed
val NovaDarkBackground = NovaOffWhite
val NovaSurfaceDark = NovaSurfaceHighest
val NovaSurfaceVariant = NovaSurfaceHighest
val NovaTextPrimary = NovaPureBlack
val NovaTextSecondary = NovaSecondaryGrey

object NovaColors {
    val background @Composable get() = MaterialTheme.colorScheme.background
    val surface @Composable get() = MaterialTheme.colorScheme.surface
    val surfaceElevated @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val surfaceContainerLowest @Composable get() = NovaSurfaceLowest
    val surfaceContainerLow @Composable get() = NovaSurfaceLow
    val surfaceContainer @Composable get() = NovaSurfaceContainer
    val surfaceContainerHigh @Composable get() = NovaSurfaceHigh
    val surfaceContainerHighest @Composable get() = NovaSurfaceHighest
    val surfaceDim @Composable get() = NovaSurfaceDim
    val textPrimary @Composable get() = MaterialTheme.colorScheme.onBackground
    val textSecondary @Composable get() = NovaSecondaryGrey
    val primary @Composable get() = MaterialTheme.colorScheme.primary
    val accent @Composable get() = MaterialTheme.colorScheme.primary
    val neonOrange @Composable get() = NovaAkzidenzRed
    val success @Composable get() = NovaPureBlack
    val error @Composable get() = NovaErrorRed
    val cardBorder @Composable get() = NovaPureBlack
    val glassSurface @Composable get() = NovaSurfaceLowest
    val glassBorder @Composable get() = NovaPureBlack
}

private val NovaModernistColorScheme = lightColorScheme(
    primary = NovaAkzidenzRed,
    onPrimary = NovaSurfaceLowest,
    primaryContainer = NovaPrimaryRedContainer,
    onPrimaryContainer = Color(0xFFFFF5F3),
    secondary = NovaSecondaryGrey,
    onSecondary = NovaSurfaceLowest,
    secondaryContainer = NovaSurfaceHighest,
    onSecondaryContainer = NovaOnSecondaryVariant,
    tertiary = Color(0xFF575959),
    onTertiary = NovaSurfaceLowest,
    background = NovaOffWhite,
    onBackground = NovaPureBlack,
    surface = NovaOffWhite,
    onSurface = NovaPureBlack,
    surfaceVariant = NovaSurfaceHighest,
    onSurfaceVariant = Color(0xFF5E3F3B),
    surfaceTint = Color(0xFFC0000C),
    outline = NovaOutlineGrey,
    outlineVariant = Color(0xFFE9BCB6),
    error = NovaErrorRed,
    onError = NovaSurfaceLowest,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

// Objective Modernist Typography Scale (Inter)
private val NovaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold, // 800
        fontSize = 72.sp,
        letterSpacing = (-0.03).sp,
        lineHeight = 72.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 48.sp,
        letterSpacing = (-0.02).sp,
        lineHeight = 52.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 32.sp,
        letterSpacing = (-0.02).sp,
        lineHeight = 36.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 20.sp,
        letterSpacing = (-0.01).sp,
        lineHeight = 30.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 16.sp,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 14.sp,
        letterSpacing = 0.05.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        lineHeight = 14.sp,
    ),
)

// Strictly zero corner radius everywhere — right-angled geometry only.
private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NovaModernistColorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content,
    )
}
