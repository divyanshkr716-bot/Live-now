package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = YouTubeRedDark,
    onPrimaryContainer = Color.White,
    secondary = LiveGreen,
    onSecondary = Color.Black,
    tertiary = WarningAmber,
    background = DarkCanvas,
    onBackground = Color(0xFFF1F1F1),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = DarkSurfaceBorder
)

private val LightColorScheme = lightColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = YouTubeRedDark,
    onPrimaryContainer = Color.White,
    secondary = LiveGreen,
    onSecondary = Color.Black,
    tertiary = WarningAmber,
    background = LightCanvas,
    onBackground = Color(0xFF0F0F0F),
    surface = LightSurface,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = Color(0xFF444444),
    outline = LightSurfaceBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek Broadcast Dark theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

