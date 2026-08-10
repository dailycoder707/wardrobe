package com.wardrobe.app.core.model.garment

/**
 * A fixed, non-user-editable vocabulary, same shape as [Fit]/[SleeveLength] —
 * a single-valued column on `garments`, not a reference table. Only
 * applicable to categories that actually have a neckline (tops, dresses,
 * jumpsuits, outerwear) — see `FieldApplicability` in `feature:capture`
 * for the category-based N/A determination.
 */
enum class Neckline { CREW, V_NECK, SCOOP, COLLARED, TURTLENECK, HOODED, OFF_SHOULDER, HALTER, OTHER }
