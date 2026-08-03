package com.wardrobe.app.core.model.tryon

/**
 * A deterministic color-grade derived from the active body profile's
 * [BodyPose.NEUTRAL] photo (`core:tryon`'s `LightingMatcher` — a histogram
 * pass and arithmetic, no ML, no cloud) applied to garment layers at
 * render time so a cutout photographed under different lighting doesn't
 * look starkly out of place against the user's own photo. **Not
 * persisted** — recomputed each session from whichever photo is currently
 * the active profile's `NEUTRAL` reference, so it never needs its own
 * cache-invalidation tracking.
 *
 * This is a color-grade heuristic, not true relighting: it will not
 * correct for a strongly directional light source the garment cutout
 * wasn't originally lit by. See `phase-10-personal-virtual-tryon.md`'s
 * Known Limitations.
 */
data class LightingAdjustment(
    val brightnessDelta: Float,
    val contrastFactor: Float,
    val colorGainR: Float,
    val colorGainG: Float,
    val colorGainB: Float,
) {
    companion object {
        /** No adjustment — used when a lighting sample isn't available
         * (e.g. no body profile yet), never a fabricated correction. */
        val NEUTRAL =
            LightingAdjustment(
                brightnessDelta = 0f,
                contrastFactor = 1f,
                colorGainR = 1f,
                colorGainG = 1f,
                colorGainB = 1f,
            )
    }
}
