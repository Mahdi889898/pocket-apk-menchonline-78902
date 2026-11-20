package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Royal Persian Tabletop Palette
val DeepIndigoBg = Color(0xFF0E1120)
val SurfaceNavy = Color(0xFF171C30)
val SurfaceElevated = Color(0xFF212842)
val SurfaceHighlight = Color(0xFF2B3456)
val RoyalGold = Color(0xFFF4B942)
val RoyalGoldLight = Color(0xFFFFD670)
val RoyalGoldDark = Color(0xFFC88C1E)
val CreamWhite = Color(0xFFF8F6F0)
val MutedSlate = Color(0xFFA2ACC7)
val BorderSubtle = Color(0xFF313B5E)
val EmeraldOnline = Color(0xFF2CE59B)
val CrimsonDanger = Color(0xFFFF5A6E)

// Board Tabletop Wood & Parchment
val BoardFrameDark = Color(0xFF3E2415)
val BoardFrameLight = Color(0xFF6B3F24)
val BoardTileLight = Color(0xFFFDF7EA)
val BoardTileAlt = Color(0xFFF2E8D5)
val BoardTileBorder = Color(0xFFD8C8AD)

// 4 Jewel Ludo Seat Palettes (0: Red, 1: Green, 2: Yellow, 3: Blue)
data class SeatPalette(
    val nameFa: String,
    val primary: Color,
    val light: Color,
    val dark: Color,
    val container: Color,
    val onPrimary: Color = Color.White,
)

val SeatPalettes = listOf(
    SeatPalette(
        nameFa = "قرمز",
        primary = Color(0xFFE83E52),
        light = Color(0xFFFF7A88),
        dark = Color(0xFFA61D2F),
        container = Color(0xFF471A26),
    ),
    SeatPalette(
        nameFa = "سبز",
        primary = Color(0xFF18B879),
        light = Color(0xFF56E3A8),
        dark = Color(0xFF0B7D50),
        container = Color(0xFF113B2B),
    ),
    SeatPalette(
        nameFa = "زرد",
        primary = Color(0xFFF5A623),
        light = Color(0xFFFFD166),
        dark = Color(0xFFB57209),
        container = Color(0xFF422F10),
        onPrimary = Color(0xFF1A1405),
    ),
    SeatPalette(
        nameFa = "آبی",
        primary = Color(0xFF2E86FF),
        light = Color(0xFF75B2FF),
        dark = Color(0xFF1355B8),
        container = Color(0xFF132B4D),
    ),
)

val AvatarColors = listOf(
    Color(0xFFE83E52),
    Color(0xFF18B879),
    Color(0xFFF5A623),
    Color(0xFF2E86FF),
    Color(0xFF9B5DE5),
    Color(0xFFF15BB5),
    Color(0xFF00BBF9),
    Color(0xFF00F5D4),
)

val AvatarEmojis = listOf("🦁", "🦅", "🦊", "🐺", "🐼", "🐯", "👑", "💎")
