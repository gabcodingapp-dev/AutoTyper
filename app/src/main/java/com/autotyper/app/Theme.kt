package com.autotyper.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MonoColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF101010),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF333333),
    error = Color(0xFFFF5C5C)
)

@Composable
fun AutoTyperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoColors, content = content)
}
