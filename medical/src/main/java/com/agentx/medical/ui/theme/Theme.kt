package com.agentx.medical.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.material3.lightColorScheme

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

private val ClinicalLightColorScheme = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
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
fun AgentXMedicalTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ClinicalObsidianColorScheme else ClinicalLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
