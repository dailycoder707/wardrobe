package com.wardrobe.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The real type scale from phase-4-design-system.md Section 3 — two typefaces,
 * each with one job: **Fraunces** (warm high-contrast serif) for editorial
 * moments (the greeting, section titles, big stat numbers), **Inter** (a
 * humanist grotesk) for everything read as information.
 *
 * **Known gap, stated rather than hidden**: the real Fraunces/Inter font
 * files are not bundled — this development environment has no way to fetch
 * binary font assets from Google Fonts. [FrauncesFamily]/[InterFamily] fall
 * back to [FontFamily.Serif]/[FontFamily.SansSerif] (system faces with a
 * comparable register — a serif with real contrast, a clean grotesk) so the
 * *scale, weight, and hierarchy* the design doc specifies are real today, and
 * swapping in the actual bundled `.ttf` files later (`res/font/`, a
 * `FontFamily(Font(R.font.fraunces_medium, FontWeight.Medium), ...)`
 * declaration) is a one-file change to this object, not a redesign. Tracked in
 * `TECHNICAL_DEBT.md`.
 */
internal val FrauncesFamily: FontFamily = FontFamily.Serif
internal val InterFamily: FontFamily = FontFamily.SansSerif

private val LabelTracking = 0.02.em

internal val WardrobeTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FrauncesFamily,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Medium,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FrauncesFamily,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Medium,
            ),
        titleLarge =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        labelMedium =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = LabelTracking,
            ),
        labelSmall =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
            ),
    )
