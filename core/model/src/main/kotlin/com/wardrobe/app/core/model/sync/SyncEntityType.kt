package com.wardrobe.app.core.model.sync

/**
 * Every independently change-tracked table (Phase 8's outbox/trigger design —
 * see `phase-8-multi-device-sync.md`). Cross-ref/collection tables (garment
 * seasons, dress codes, tags, materials, colors; outfit seasons/dress
 * codes/tags/slot composition) are **not** listed here — they ride along
 * inside their parent [GARMENT]/[OUTFIT] payload and are merged as sets, per
 * the brief's "Collections: Merge" rule, rather than tracked as their own
 * rows. `weather_cache`/`stats_cache` are also deliberately absent: one is
 * device-local (a forecast for *this* device's location), the other is a
 * derived cache recomputed from synced data, not a source of truth.
 *
 * Phase 10 adds [BODY_PROFILE], [GARMENT_PLACEMENT_TEMPLATE],
 * [GARMENT_MASK] — the on-device body profile and per-garment try-on
 * placements/masks are eligible for the same local-network sync every
 * other personal data type already uses (Constitution rule 13/ADR-011's
 * one narrow exception for "the user's own paired devices"). Body
 * reference photos and `body_measurements` are *not* separate entries —
 * they ride along inside [BODY_PROFILE]'s own change payload, the same
 * "collections ride along with their parent" convention already used for
 * e.g. garment seasons/tags.
 */
enum class SyncEntityType {
    CATEGORY,
    COLOR,
    MATERIAL,
    BRAND,
    TAG,
    OCCASION,
    GARMENT,
    OUTFIT,
    WEAR_EVENT,
    STYLE_RULE,
    FEEDBACK,
    TRIP,
    TRIP_ACTIVITY,
    PACKING_LIST_ITEM,
    WISHLIST_ITEM,
    IMAGE,
    BODY_PROFILE,
    GARMENT_PLACEMENT_TEMPLATE,
    GARMENT_MASK,
}
