package com.agentx.admin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF38BDF8),
    surface = SurfaceDark,
    surfaceVariant = CardDark,
    background = Color(0xFF0B0F19),
    error = CrimsonError,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = Color(0xFF0284C7),
    surface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFFF3F4F6),
    background = Color.White,
    error = CrimsonError,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

@Composable
fun AgentXAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
