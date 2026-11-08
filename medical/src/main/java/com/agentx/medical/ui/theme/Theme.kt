package com.agentx.medical.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClinicalObsidianColorScheme = darkColorScheme(
    primary = ClinicalTealBright,
    onPrimary = MedicalVoid,
    primaryContainer = Color(0xFF042F2E),
    onPrimaryContainer = ClinicalTealBright,
    secondary = CyanCaduceus,
    onSecondary = MedicalVoid,
    secondaryContainer = Color(0xFF0E334D),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = BioEmerald,
    onTertiary = MedicalVoid,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = MedicalVoid,
    onBackground = ClinicalTextPrimary,
    surface = MedicalSurface,
    onSurface = ClinicalTextPrimary,
    surfaceVariant = MedicalSurfaceGlass,
    onSurfaceVariant = ClinicalTextSecondary,
    outline = MedicalSurfaceBorder,
    outlineVariant = Color(0xFF1E3A5F),
    error = ClinicalAlert,
    onError = Color.White
)

@Composable
fun AgentXMedicalTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClinicalObsidianColorScheme,
        typography = Typography,
        content = content
    )
}
