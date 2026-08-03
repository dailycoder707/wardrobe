package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.common.BodyProfileId
import java.time.Instant

/**
 * Phase 10 — the four guided captures a body profile is built from. Front-
 * facing only (see `phase-10-personal-virtual-tryon.md`'s capture design):
 * multi-angle capture was deliberately excluded from v1 since garment
 * cutouts are single-angle already, so a side/¾ body photo wouldn't improve
 * matching fidelity, only add scope.
 */
enum class BodyPose {
    /** The canonical try-on render background. */
    NEUTRAL,

    /** Pose-detection-friendlier shoulder/armpit separation — never shown
     * as a render background, only used to improve landmark confidence. */
    ARMS_OUT,

    /** Close-up for belt/hem anchor precision. */
    TORSO,

    /** Close-up for footwear anchor precision — the hardest garment
     * category this feature composites (see anchor-region fidelity notes
     * on [TryOnAnchorRegion]). */
    FEET,
}

/**
 * One of the four guided photos backing a [BodyProfile]. Stored via
 * `core:tryon`'s `BodyImageFileStore`, a sibling of (not an extension of)
 * `core:image`'s `ImageFileStore` — a body photo is not a garment
 * `ImageType.ORIGINAL`/`CUTOUT`/`THUMBNAIL`.
 */
data class BodyReferencePhoto(
    val pose: BodyPose,
    val filePath: String,
    val width: Int,
    val height: Int,
)

/**
 * Phase 10 — a private, on-device-only body profile: the guided reference
 * photos themselves (needed as the actual try-on render background) plus
 * whatever [BodyMeasurements] have been derived from them. Single-profile-
 * per-device for v1 (`BodyProfileRepository.observeBodyProfile()` has no id
 * parameter) — a deliberate v1 scope decision, not a technical ceiling.
 *
 * Eligible for Phase 8's existing encrypted local-network device-to-device
 * sync (never touching anything beyond the user's own paired devices) —
 * this is the one narrow exception Constitution rule 13/ADR-011 already
 * carves out for personal photos.
 */
data class BodyProfile(
    val id: BodyProfileId,
    val label: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val photos: List<BodyReferencePhoto>,
)
