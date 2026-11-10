package com.agentx.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.material3.lightColorScheme

private val ServerObsidianColorScheme = darkColorScheme(
    primary = MatrixGreenBright,
    onPrimary = ServerVoid,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = MatrixGreenBright,
    secondary = CyberBlue,
    onSecondary = ServerVoid,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = ServerVoid,
    onBackground = ServerTextPrimary,
    surface = ServerSurface,
    onSurface = ServerTextPrimary,
    surfaceVariant = ServerGlass,
    onSurfaceVariant = ServerTextSecondary,
    outline = ServerBorder,
    outlineVariant = Color(0xFF263859),
    error = RoseError,
    onError = Color.White
)

private val ServerLightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun AgentXAdminTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ServerObsidianColorScheme else ServerLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
