package com.openreel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFFFC83D)
val Crimson = Color(0xFFE8364F)
val Green = Color(0xFF4CD787)
val Amber = Color(0xFFFFB547)

private val Colors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF221A00),
    secondary = Crimson,
    onSecondary = Color.White,
    background = Color(0xFF141018),
    onBackground = Color(0xFFF1ECF4),
    surface = Color(0xFF141018),
    onSurface = Color(0xFFF1ECF4),
    surfaceVariant = Color(0xFF2A2430),
    onSurfaceVariant = Color(0xFFC3BACB),
    surfaceContainer = Color(0xFF1E1924),
    surfaceContainerHigh = Color(0xFF28222F),
    outline = Color(0xFF6E6477),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
)

/** Always dark: it's a cinema. */
@Composable
fun OpenReelTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, content = content)
