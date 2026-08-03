package com.wardrobe.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * phase-4-design-system.md Section 5 — a soft, warm-toned diffused shadow
 * ("softbox product photography"), explicitly not Material's tinted-elevation
 * system. The design doc specifies each level as an independent y-offset +
 * blur radius + opacity; Compose's [Modifier.shadow] models shadow as a single
 * elevation value with an ambient/spot tint rather than exposing offset/blur
 * separately, so [level] maps to that one elevation value (using the spec's
 * own y-offset dp as the elevation) — a deliberate, documented approximation
 * of the design doc's box-shadow-style spec, not a literal implementation of
 * "blur:Xdp" (a true independent blur would need a custom `RenderEffect`
 * layer, out of scope for the visual return it would buy here).
 */
enum class WardrobeElevation(
    val dp: Dp,
) {
    RESTING(2.dp),
    RAISED(6.dp),
    FLOATING(10.dp),
    MODAL(16.dp),
}

private val LightShadowTint = Color(0xFF1F1B12)
private val DarkShadowTint = Color(0xFF05070A)

@Composable
fun Modifier.wardrobeShadow(
    level: WardrobeElevation,
    shape: Shape = RectangleShape,
): Modifier {
    val dark = isSystemInDarkTheme()
    val tint = if (dark) DarkShadowTint else LightShadowTint
    // Dark theme needs roughly double the opacity to read as a shadow at all
    // against a dark surface (design doc, Section 5) — alpha channel folded
    // into the tint color itself since `shadow`'s ambient/spot colors carry
    // their own alpha.
    val alpha = if (dark) SHADOW_ALPHA_DARK else SHADOW_ALPHA_LIGHT
    val tinted = tint.copy(alpha = alpha)
    return this.shadow(
        elevation = level.dp,
        shape = shape,
        ambientColor = tinted,
        spotColor = tinted,
    )
}

private const val SHADOW_ALPHA_LIGHT = 0.10f
private const val SHADOW_ALPHA_DARK = 0.20f
