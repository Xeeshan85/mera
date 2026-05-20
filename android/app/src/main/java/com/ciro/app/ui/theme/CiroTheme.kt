package com.ciro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * CIRO Material 3 theme — dark command-center aesthetic.
 *
 * Wrap the entire app in CiroTheme {} so all Material 3 components
 * automatically pick up the CIRO palette.
 */

private val CiroDarkColorScheme = darkColorScheme(
    primary = CiroColors.AccentCyan,
    onPrimary = CiroColors.TextOnAccent,
    primaryContainer = CiroColors.AccentCyan.copy(alpha = 0.15f),
    onPrimaryContainer = CiroColors.AccentCyan,

    secondary = CiroColors.AccentPurple,
    onSecondary = CiroColors.TextOnAccent,
    secondaryContainer = CiroColors.AccentPurple.copy(alpha = 0.15f),
    onSecondaryContainer = CiroColors.AccentPurple,

    tertiary = CiroColors.AccentGold,
    onTertiary = CiroColors.TextOnAccent,

    error = CiroColors.AccentRed,
    onError = CiroColors.TextPrimary,
    errorContainer = CiroColors.AccentRed.copy(alpha = 0.15f),
    onErrorContainer = CiroColors.AccentRed,

    background = CiroColors.Surface,
    onBackground = CiroColors.TextPrimary,

    surface = CiroColors.Surface,
    onSurface = CiroColors.TextPrimary,
    surfaceVariant = CiroColors.SurfaceCard,
    onSurfaceVariant = CiroColors.TextSecondary,

    outline = CiroColors.SurfaceBorder,
    outlineVariant = CiroColors.SurfaceBorder.copy(alpha = 0.5f),

    inverseSurface = CiroColors.TextPrimary,
    inverseOnSurface = CiroColors.Surface,
    inversePrimary = CiroColors.AccentCyan,

    surfaceTint = CiroColors.AccentCyan.copy(alpha = 0.05f),
)

private val CiroShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CiroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CiroDarkColorScheme,
        typography = CiroTypography,
        shapes = CiroShapes,
        content = content,
    )
}
