package com.agentx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberObsidianColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidDark,
    primaryContainer = Color(0xFF0E2A47),
    onPrimaryContainer = ElectricCyan,
    secondary = NeonIndigo,
    onSecondary = VoidDark,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = AuroraViolet,
    onTertiary = VoidDark,
    tertiaryContainer = Color(0xFF2E1065),
    onTertiaryContainer = Color(0xFFDDD6FE),
    background = VoidDark,
    onBackground = TextPrimary,
    surface = SpaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGlass,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    outlineVariant = Color(0xFF334155),
    error = AlertRose,
    onError = Color.White
)

@Composable
fun AgentXTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberObsidianColorScheme,
        typography = Typography,
        content = content
    )
}
