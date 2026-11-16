package com.efeyamann.dizi

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark cinema palette: near-black surfaces so posters carry the color, one rose accent.
private val Bg = Color(0xFF0E1014)
private val Raised = Color(0xFF171A21)
private val Colors = darkColorScheme(
    primary = Color(0xFFFB7185),
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF4C0519),
    secondaryContainer = Color(0xFF3B1A24),
    onSecondaryContainer = Color(0xFFFFE4E6),
    background = Bg,
    onBackground = Color(0xFFF1F5F9),
    surface = Bg,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E222B),
    onSurfaceVariant = Color(0xFFA3ADBD),
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Raised,
    surfaceContainer = Raised,
    surfaceContainerHigh = Color(0xFF1E222B),
    surfaceContainerHighest = Color(0xFF262B36),
    outline = Color(0xFF4A5160),
    outlineVariant = Color(0xFF2A2F3A),
)

@Composable
fun DiziTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)
