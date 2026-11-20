package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MenchDarkColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = Color(0xFF1A1405),
    primaryContainer = RoyalGoldDark,
    onPrimaryContainer = CreamWhite,
    secondary = EmeraldOnline,
    onSecondary = Color(0xFF062619),
    secondaryContainer = Color(0xFF133A2E),
    onSecondaryContainer = CreamWhite,
    tertiary = SeatPalettes[3].primary,
    onTertiary = Color.White,
    background = DeepIndigoBg,
    onBackground = CreamWhite,
    surface = SurfaceNavy,
    onSurface = CreamWhite,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = MutedSlate,
    outline = BorderSubtle,
    error = CrimsonDanger,
    onError = Color.White,
)

private val MenchShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MenchTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MenchDarkColorScheme,
        typography = AppTypography,
        shapes = MenchShapes,
        content = content,
    )
}
