package com.example.spotapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spotapp.ui.theme.components.SpotPalette

// The SPOT interface is designed as a light white/gold theme. Keeping this
// self-contained prevents the theme from becoming unresolved when template
// Color.kt or Type.kt files are moved or renamed.
// Every role is set: roles left to Material's defaults (containers, outlines,
// surface containers) render lavender in dialogs, menus, sheets and chips.
private val SpotLightColorScheme = lightColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFFFFF1CB),
    onPrimaryContainer = Color(0xFF3D2E00),
    inversePrimary = Color(0xFFF2D27A),
    secondary = Color(0xFFB58E22),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF6D9),
    onSecondaryContainer = Color(0xFF3D2E00),
    tertiary = Color(0xFFAD8420),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7ECCB),
    onTertiaryContainer = Color(0xFF3A2C05),
    background = SpotPalette.Page,
    onBackground = Color(0xFF171717),
    surface = Color.White,
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF2EEE4),
    onSurfaceVariant = Color(0xFF74746C),
    surfaceTint = Color(0xFFD4AF37),
    inverseSurface = Color(0xFF26251F),
    inverseOnSurface = Color(0xFFFFF8E6),
    error = Color(0xFFD84A4A),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF5F1412),
    outline = Color(0xFFB9B3A5),
    outlineVariant = Color(0xFFE8E2D5),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE9E5DA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SpotPalette.Ivory,
    surfaceContainer = Color(0xFFFBF7EE),
    surfaceContainerHigh = Color(0xFFF7F2E6),
    surfaceContainerHighest = Color(0xFFF1EBDD)
)

// Charcoal with the same warm gold. Every role is set here too, for the same reason as above.
private val SpotDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF4A3B0C),
    onPrimaryContainer = Color(0xFFFFE9A8),
    // Snackbars and tooltips use the inverse colours; SPOT keeps them dark in both themes, so here they're a raised
    // charcoal with light text and a light gold action instead of Material's light patch.
    inversePrimary = Color(0xFFE6C766),
    secondary = Color(0xFFE0BC55),
    onSecondary = Color(0xFF241C00),
    secondaryContainer = Color(0xFF3A3118),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Color(0xFFD9AE3A),
    onTertiary = Color(0xFF241C00),
    tertiaryContainer = Color(0xFF3F3310),
    onTertiaryContainer = Color(0xFFF7ECCB),
    background = Color(0xFF121110),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF232220),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF2E2C26),
    onSurfaceVariant = Color(0xFFA8A69B),
    surfaceTint = Color(0xFFD4AF37),
    inverseSurface = Color(0xFF34322C),
    inverseOnSurface = Color(0xFFF2EFE6),
    error = Color(0xFFE27A7A),
    onError = Color(0xFF3A0B0B),
    errorContainer = Color(0xFF5F1412),
    onErrorContainer = Color(0xFFFDE8E6),
    outline = Color(0xFF6B6859),
    outlineVariant = Color(0xFF3A3830),
    scrim = Color.Black,
    surfaceBright = Color(0xFF302E2A),
    surfaceDim = Color(0xFF121110),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF1B1A17),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF292723),
    surfaceContainerHighest = Color(0xFF33312C)
)

// Softer, consistent corners for Material components (fields, menus, cards, chips, snackbars).
private val SpotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SPOTAppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Written before any screen composes, so every SpotPalette read in this pass sees the new value.
    if (SpotPalette.dark != dark) SpotPalette.dark = dark
    MaterialTheme(
        colorScheme = if (dark) SpotDarkColorScheme else SpotLightColorScheme,
        typography = Typography,
        shapes = SpotShapes,
        content = { com.example.spotapp.ui.theme.components.ProvideSpotAmbientMotion(content) }
    )
}
