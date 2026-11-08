package com.agentx.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun AgentXAdminTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ServerObsidianColorScheme,
        typography = Typography,
        content = content
    )
}
