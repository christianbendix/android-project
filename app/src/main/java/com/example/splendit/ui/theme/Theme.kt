package com.example.splendit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPalette = staticCompositionLocalOf { LightPalette }

private fun SplitwheelPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent400,
        onPrimary = surface,
        primaryContainer = accent800,
        onPrimaryContainer = accent200,
        secondary = accent300,
        onSecondary = surface,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = neutral900,
        onSurfaceVariant = neutral500,
        outline = neutral700,
        outlineVariant = neutral800
    )
}

/** Wraps the whole app (Lecture 8). Dynamic colour is off so the brand palette always wins. */
@Composable
fun SplenditTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}

/** Access the full Splitwheel ramp: `Sw.colors.accent300`. */
object Sw {
    val colors: SplitwheelPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPalette.current
}
