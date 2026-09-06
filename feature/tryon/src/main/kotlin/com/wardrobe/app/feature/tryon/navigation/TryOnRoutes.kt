package com.wardrobe.app.feature.tryon.navigation

import kotlinx.serialization.Serializable

/** Launched on first "Try On Me" tap with no existing body profile, and
 * independently from Settings for re-capture (which re-triggers
 * `BodyProfileRepository.recomputeMeasurements()` once every pose is
 * re-captured). See `phase-10-personal-virtual-tryon.md`'s guided-capture
 * design for the four-pose sequence this route walks through. */
@Serializable
object BodyProfileCaptureRoute

/** The shared "Try On Me" entry point every integration surface (Outfit
 * Detail, Today's Recommendation, Saved Looks, Trip Planner) navigates to —
 * dual-input, directly modeled on `OutfitPreviewRoute`'s shape: a real,
 * persisted [outfitId] when one exists, or a raw comma-joined
 * [garmentIds] list when it doesn't (Today's Recommendation's
 * not-yet-saved scored outfit). Wired into `WardrobeNavHost` in a later
 * milestone — this module owns the route's shape, not its call sites. */
@Serializable
data class TryOnRoute(
    val outfitId: Long? = null,
    val garmentIds: String? = null,
)

/** Reached from a per-layer "Edit mask" action inside [TryOnRoute] — a
 * dedicated erase/restore mode over one garment's own cutout (see
 * `core:tryon`'s `GarmentMaskEditor` KDoc for why this is manual-only, no
 * auto-segmentation). */
@Serializable
data class MaskEditorRoute(
    val garmentId: Long,
)

/** M12's Try-On Review comparison viewer — reached from a per-layer
 * "Compare with Cloud" action inside [TryOnRoute], the same way
 * [MaskEditorRoute] is reached from "Edit mask." */
@Serializable
data class TryOnCompareRoute(
    val garmentId: Long,
)
