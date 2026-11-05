package com.bhatiafeeds.books.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF2E6B45)
val DueRed = Color(0xFFB3261E)
val Muted = Color(0xFF6B6E68)
val Hairline = Color(0xFFE3E4DD)

private val scheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF123120),
    secondary = Color(0xFF55645A),
    secondaryContainer = Color(0xFFE4EBE5),
    onSecondaryContainer = Color(0xFF1B2A20),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFAFAF7),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFF0F1EA),
    onSurfaceVariant = Color(0xFF5A5C57),
    outline = Color(0xFFB9BCB3),
    outlineVariant = Hairline,
    error = DueRed
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
