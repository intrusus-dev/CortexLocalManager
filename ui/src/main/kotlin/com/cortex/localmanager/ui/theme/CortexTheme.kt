package com.cortex.localmanager.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Colors ---

object CortexColors {
    val Background = Color(0xFF121218)
    val Surface = Color(0xFF1E1E2E)
    val SurfaceVariant = Color(0xFF262638)
    val SurfaceBright = Color(0xFF2A2A3C)

    val PaloAltoOrange = Color(0xFFFA582D)
    val OrangeDim = Color(0x33FA582D)  // 20% opacity

    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFFA0A0B0)
    val TextMuted = Color(0xFF6A6A7A)

    val Error = Color(0xFFE53935)
    val Warning = Color(0xFFFB8C00)
    val Success = Color(0xFF43A047)
    val Info = Color(0xFF42A5F5)

    val SeverityCritical = Color(0xFFE53935)
    val SeverityHigh = Color(0xFFFB8C00)
    val SeverityMedium = Color(0xFFFFCA28)
    val SeverityLow = Color(0xFF42A5F5)
    val SeverityInfo = Color(0xFF78909C)

    val Border = Color(0xFF2E2E42)
    val Divider = Color(0xFF2A2A3C)
}

private val CortexDarkColorScheme = darkColorScheme(
    primary = CortexColors.PaloAltoOrange,
    onPrimary = Color.White,
    primaryContainer = CortexColors.OrangeDim,
    onPrimaryContainer = CortexColors.PaloAltoOrange,
    secondary = CortexColors.TextSecondary,
    onSecondary = Color.White,
    background = CortexColors.Background,
    onBackground = CortexColors.TextPrimary,
    surface = CortexColors.Surface,
    onSurface = CortexColors.TextPrimary,
    surfaceVariant = CortexColors.SurfaceVariant,
    onSurfaceVariant = CortexColors.TextSecondary,
    error = CortexColors.Error,
    onError = Color.White,
    outline = CortexColors.Border,
    outlineVariant = CortexColors.Divider
)

// --- Typography ---

object CortexFonts {
    val Mono = FontFamily.Monospace
}

private val CortexTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
        color = CortexColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = CortexColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = CortexColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = CortexColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = CortexColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = CortexColors.TextSecondary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = CortexColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = CortexColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        color = CortexColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        color = CortexColors.TextMuted
    )
)

// --- Shapes ---

private val CortexShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

@Composable
fun CortexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CortexDarkColorScheme,
        typography = CortexTypography,
        shapes = CortexShapes,
        content = content
    )
}
