package com.example.splendit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Splitwheel design tokens, taken from the v3 prototype. Ramps run from strongest contrast
 * against the background (100) to weakest (900), so the same step reads the same in light and
 * dark mode.
 */
@Immutable
data class SplitwheelPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val accent: Color,
    val divider: Color,
    val neutral100: Color,
    val neutral200: Color,
    val neutral300: Color,
    val neutral400: Color,
    val neutral500: Color,
    val neutral600: Color,
    val neutral700: Color,
    val neutral800: Color,
    val neutral900: Color,
    val accent100: Color,
    val accent200: Color,
    val accent300: Color,
    val accent400: Color,
    val accent500: Color,
    val accent600: Color,
    val accent700: Color,
    val accent800: Color,
    val accent900: Color
)

val LightPalette = SplitwheelPalette(
    background = Color(0xFFEEF0F7),
    surface = Color(0xFFFBFBFE),
    text = Color(0xFF1B1D2A),
    accent = Color(0xFF2F7FD6),
    divider = Color(0x1F1B1D2A),
    neutral100 = Color(0xFF1F2130),
    neutral200 = Color(0xFF2E3140),
    neutral300 = Color(0xFF454857),
    neutral400 = Color(0xFF5C6072),
    neutral500 = Color(0xFF6F7386),
    neutral600 = Color(0xFF8E92A4),
    neutral700 = Color(0xFFB8BBC9),
    neutral800 = Color(0xFFDCDEE8),
    neutral900 = Color(0xFFE8EAF2),
    accent100 = Color(0xFF0F2A4A),
    accent200 = Color(0xFF17406E),
    accent300 = Color(0xFF1F5FA3),
    accent400 = Color(0xFF2F7FD6),
    accent500 = Color(0xFF4B98E8),
    accent600 = Color(0xFF7CB6F0),
    accent700 = Color(0xFFB3D4F7),
    accent800 = Color(0xFFDCEAFB),
    accent900 = Color(0xFFEEF5FD)
)

/** Dark variant: the Nocturne grounds the prototype was built on, with the blue accent. */
val DarkPalette = SplitwheelPalette(
    background = Color(0xFF161826),
    surface = Color(0xFF232532),
    text = Color(0xFFE9E9ED),
    accent = Color(0xFF4B98E8),
    divider = Color(0x29E9E9ED),
    neutral100 = Color(0xFFF3F5FE),
    neutral200 = Color(0xFFE4E7F5),
    neutral300 = Color(0xFFCFD3E5),
    neutral400 = Color(0xFFB2B6CA),
    neutral500 = Color(0xFF9397AB),
    neutral600 = Color(0xFF75798C),
    neutral700 = Color(0xFF595D6C),
    neutral800 = Color(0xFF3F424D),
    neutral900 = Color(0xFF292B31),
    accent100 = Color(0xFFEEF5FD),
    accent200 = Color(0xFFDCEAFB),
    accent300 = Color(0xFF7CB6F0),
    accent400 = Color(0xFF2F7FD6),
    accent500 = Color(0xFF4B98E8),
    accent600 = Color(0xFF1F5FA3),
    accent700 = Color(0xFF17406E),
    accent800 = Color(0xFF1A2F4C),
    accent900 = Color(0xFF14243A)
)
