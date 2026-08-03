package com.wardrobe.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val AtelierLightColors =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        error = LightError,
        onError = LightOnError,
        background = LightBackground,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurface,
        onSurfaceVariant = LightTextSecondary,
        surfaceContainerHigh = LightSurfaceElevated,
        outline = LightBorder,
    )

private val AtelierNightColors =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        error = DarkError,
        onError = DarkOnError,
        background = DarkBackground,
        onBackground = DarkTextPrimary,
        surface = DarkSurface,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkSurface,
        onSurfaceVariant = DarkTextSecondary,
        surfaceContainerHigh = DarkSurfaceElevated,
        outline = DarkBorder,
    )

internal val LocalWardrobeExtendedColors =
    staticCompositionLocalOf {
        // Real defaults (Atelier Light), not `Color.Unspecified` — a composable
        // read outside `WardrobeTheme` (a Compose preview, a test) should still
        // render something correct rather than transparent/invisible text.
        LightExtendedColors
    }

/** Non-Material3 tokens (`accent`, `success`, `warning`, `textSecondary`) that
 * this design system needs and `MaterialTheme.colorScheme` has no slot for —
 * see `Color.kt`'s top-of-file doc comment for the full mapping rationale. */
object WardrobeTheme {
    val extendedColors: WardrobeExtendedColors
        @Composable get() = LocalWardrobeExtendedColors.current
}

/**
 * App-wide Material3 theme, now backed by the real Atelier Light/Night palette
 * (phase-4-design-system.md Section 2) rather than the Phase 2 seed colors. No
 * dynamic-color option: this is a styling app whose own brand palette is a
 * deliberate product decision, not something that should defer to the user's
 * wallpaper (Section 2's own framing).
 */
@Composable
fun WardrobeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AtelierNightColors else AtelierLightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWardrobeExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WardrobeTypography,
            shapes = WardrobeShapes,
            content = content,
        )
    }
}
