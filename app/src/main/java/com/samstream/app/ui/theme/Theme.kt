package com.samstream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand
val Gold = Color(0xFFFFC83D)
val GoldDeep = Color(0xFFFF9A1F)
val Crimson = Color(0xFFE8364F)
val Green = Color(0xFF4CD787)
val Amber = Color(0xFFFFB547)

// Cinema surfaces
val Ink = Color(0xFF0B0A10)
val InkRaised = Color(0xFF15131C)
val InkHigh = Color(0xFF1F1C28)
val Glass = Color(0x33FFFFFF)
val Hairline = Color(0x1FFFFFFF)
val TextSoft = Color(0xFFB9B2C6)

/** Gold → amber, used for the main action and the brand mark. */
val GoldGradient = Brush.horizontalGradient(listOf(Gold, GoldDeep))

/** Fades an image into the page background from the bottom. */
val BottomScrim = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Ink.copy(alpha = 0.55f), 1f to Ink)
val TopScrim = Brush.verticalGradient(0f to Color(0xAA000000), 1f to Color.Transparent)

private val Colors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1C1400),
    secondary = Crimson,
    onSecondary = Color.White,
    tertiary = GoldDeep,
    background = Ink,
    onBackground = Color(0xFFF4F0F8),
    surface = Ink,
    onSurface = Color(0xFFF4F0F8),
    surfaceVariant = InkHigh,
    onSurfaceVariant = TextSoft,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF2A2634),
    outline = Color(0xFF4A4455),
    outlineVariant = Hairline,
    error = Color(0xFFFF6B7D),
    errorContainer = Color(0xFF3A1219),
    onErrorContainer = Color(0xFFFFD9DE),
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

/** Always dark: it's a cinema. */
@Composable
fun SamStreamTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, content = content)
