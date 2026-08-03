package com.wardrobe.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Atelier Light / Atelier Night — the real palette from phase-4-design-system.md
 * Section 2, verified against WCAG AA in both themes there. Material3's
 * `ColorScheme` doesn't have a slot for every named token in the design doc, so
 * the mapping below is deliberate, not 1:1: `surfaceElevated` uses M3's
 * `surfaceContainerHigh` tonal level (a real, already-distinct surface, not a
 * repurposed one), `border` uses `outline`, `textPrimary`/`textSecondary` use
 * `onBackground`/`onSurfaceVariant`. `accent`/`onAccent`/`success`/`warning`
 * have no M3 equivalent at all and are carried by [WardrobeExtendedColors]
 * instead of being forced into `tertiary`, which would read confusingly in
 * component code (`colorScheme.tertiary` for "the favorite star's gold" is not
 * self-explanatory six months from now).
 */

internal val LightBackground = Color(0xFFFAF7F2)
internal val LightSurface = Color(0xFFF5F0E8)
internal val LightSurfaceElevated = Color(0xFFFFFFFF)
internal val LightPrimary = Color(0xFF1F3D34)
internal val LightOnPrimary = Color(0xFFFAF7F2)
internal val LightSecondary = Color(0xFF3A3D42)
internal val LightOnSecondary = Color(0xFFFAF7F2)
internal val LightAccent = Color(0xFFC9A667)
internal val LightOnAccent = Color(0xFF2A2416)
internal val LightBorder = Color(0xFFE4DCCB)
internal val LightTextPrimary = Color(0xFF232527)
internal val LightTextSecondary = Color(0xFF6E6A62)
internal val LightSuccess = Color(0xFF6B8F71)
internal val LightWarning = Color(0xFFC08B4C)
internal val LightError = Color(0xFFB15C4A)
internal val LightOnError = Color(0xFFFAF7F2)

// --- Atelier Night ---
internal val DarkBackground = Color(0xFF12181C)
internal val DarkSurface = Color(0xFF1E2226)
internal val DarkSurfaceElevated = Color(0xFF262B30)
internal val DarkPrimary = Color(0xFF4C7A67)
internal val DarkOnPrimary = Color(0xFF0D1512)
internal val DarkSecondary = Color(0xFFA8A296)
internal val DarkOnSecondary = Color(0xFF0D1512)
internal val DarkAccent = Color(0xFFD8B98A)
internal val DarkOnAccent = Color(0xFF241B0C)
internal val DarkBorder = Color(0xFF2E3338)
internal val DarkTextPrimary = Color(0xFFF2EEE6)
internal val DarkTextSecondary = Color(0xFFA6A196)
internal val DarkSuccess = Color(0xFF7FA98A)
internal val DarkWarning = Color(0xFFD2A05E)
internal val DarkError = Color(0xFFC97C68)
internal val DarkOnError = Color(0xFF0D1512)

/** Every token the design doc names that has no Material3 `ColorScheme` slot —
 * see this file's top-level doc comment for which tokens *did* find a home in
 * `ColorScheme` instead of here. */
data class WardrobeExtendedColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val textSecondary: Color,
)

internal val LightExtendedColors =
    WardrobeExtendedColors(
        accent = LightAccent,
        onAccent = LightOnAccent,
        success = LightSuccess,
        warning = LightWarning,
        textSecondary = LightTextSecondary,
    )

internal val DarkExtendedColors =
    WardrobeExtendedColors(
        accent = DarkAccent,
        onAccent = DarkOnAccent,
        success = DarkSuccess,
        warning = DarkWarning,
        textSecondary = DarkTextSecondary,
    )
