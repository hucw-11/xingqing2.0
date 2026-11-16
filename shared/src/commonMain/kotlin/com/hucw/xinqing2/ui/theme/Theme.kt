package com.hucw.xinqing2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6F68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF7A5C3E),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1B1C1B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D2CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0F4C47),
    onPrimaryContainer = Color(0xFFB2DFDA),
    secondary = Color(0xFFE3C3A0),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF171A18),
    onSurface = Color(0xFFE2E3E0),
)

@Composable
fun XinqingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
