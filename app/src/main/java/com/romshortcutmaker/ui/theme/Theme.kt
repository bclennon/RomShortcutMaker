package com.romshortcutmaker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFF38BDF8),
    tertiary = Color(0xFF22C55E),
    background = Color(0xFF0F172A),
    surface = Color(0xFF111827),
    onPrimary = Color(0xFF111827),
    onSecondary = Color(0xFF0F172A),
    onTertiary = Color(0xFF0F172A),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun RomShortcutMakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
