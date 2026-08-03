package com.wardrobe.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** phase-4-design-system.md Section 6 — generously rounded throughout, never a
 * full pill (buttons are rounded rectangles). Exposed both as per-component `dp`
 * constants (since the design doc specifies radius *per component*, not per
 * M3's generic 5-tier scale) and as a [Shapes] object for the handful of
 * places Material3 components read shapes directly from [MaterialTheme]. */
object WardrobeRadius {
    val chip = 8.dp
    val button = 14.dp
    val tile = 16.dp
    val heroCard = 20.dp
    val sheet = 28.dp
    val thumbnailInset = 12.dp
}

internal val WardrobeShapes =
    Shapes(
        extraSmall = RoundedCornerShape(WardrobeRadius.chip),
        small = RoundedCornerShape(WardrobeRadius.button),
        medium = RoundedCornerShape(WardrobeRadius.tile),
        large = RoundedCornerShape(WardrobeRadius.heroCard),
        extraLarge = RoundedCornerShape(WardrobeRadius.sheet),
    )
