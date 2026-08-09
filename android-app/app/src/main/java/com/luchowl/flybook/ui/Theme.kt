package com.luchowl.flybook.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep, technical "aviation nerd" palette
private val Dark = darkColorScheme(
    primary = Color(0xFF60A5FA),        // aviation blue
    onPrimary = Color(0xFF08101F),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFFF59E0B),      // amber accent
    onSecondary = Color(0xFF08101F),
    tertiary = Color(0xFF22D3EE),       // cyan
    onTertiary = Color(0xFF08101F),
    background = Color(0xFF0B1220),     // very dark navy
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF141D2C),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2C3D55),
    outlineVariant = Color(0xFF233248),
    error = Color(0xFFF87171),
    onError = Color(0xFF08101F),
)

@Composable
fun FlybookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
