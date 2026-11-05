package com.example.ratecalc

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FB),
    onPrimaryContainer = Color(0xFF14286B),
    secondaryContainer = Color(0xFFE0F0FB),
    onSecondaryContainer = Color(0xFF0B4C7A),
    tertiary = Color(0xFF0E9AE0),
    background = Color(0xFFF7F9FD),
    surface = Color.White,
    onSurface = Color(0xFF0E1530),
    onSurfaceVariant = Color(0xFF4A5063)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0B2A80),
    primaryContainer = Color(0xFF1F3A7A),
    onPrimaryContainer = Color(0xFFDCE8FB),
    secondaryContainer = Color(0xFF1E4A66),
    onSecondaryContainer = Color(0xFFD6EEFF),
    tertiary = Color(0xFF5CC8FF),
    background = Color(0xFF0E1220),
    surface = Color(0xFF1A2030),
    onSurface = Color(0xFFE4E8F5),
    onSurfaceVariant = Color(0xFFB0B7CC)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
