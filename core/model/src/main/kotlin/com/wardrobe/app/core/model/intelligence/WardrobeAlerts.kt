package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.GarmentId

/** The four "hasn't been worn in a while" thresholds the brief asks for —
 * each enum constant's own name already documents what its `days` literal
 * means, so a `MagicNumber` suppression here is more honest than inventing
 * four named constants no clearer than `THIRTY(30)` already is. */
@Suppress("MagicNumber")
enum class ForgottenBucket(
    val days: Int,
) {
    THIRTY(30),
    SIXTY(60),
    NINETY(90),
    ONE_EIGHTY(180),
}

/** A garment eligible to be brought back into rotation — [bucket] is the
 * largest threshold it has crossed (a garment unworn 95 days is [ForgottenBucket.NINETY],
 * not also flagged as 30/60). */
data class ForgottenGarment(
    val garmentId: GarmentId,
    val daysSinceWorn: Int,
    val bucket: ForgottenBucket,
)

/** A garment worn significantly more than its wardrobe peers — [wearCount]
 * vs. [wardrobeAverageWearCount] (the mean across all active garments), so
 * the UI can say "worn 3x more than the average item" without hardcoding a
 * threshold. */
data class OverusedGarment(
    val garmentId: GarmentId,
    val wearCount: Int,
    val wardrobeAverageWearCount: Double,
)

/** Why a garment has zero wear events — see `WardrobeIntelligenceRepositoryImpl`'s
 * KDoc for why there is no `IMPORTED_NEVER_WORN` case: this schema has no
 * import-source flag on `Garment`, so that distinction can't be honestly
 * derived (documented gap, not an invented flag). */
enum class NeverWornReason { RECENTLY_ADDED, PURCHASED_LONG_AGO }

data class NeverWornGarment(
    val garmentId: GarmentId,
    val reason: NeverWornReason,
    val daysSinceAdded: Int,
)

/** The three "needs attention" alert lists bundled into one query —
 * folded together on [WardrobeIntelligenceRepository][com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository]
 * to keep that interface under detekt's function-count ceiling, since all
 * three are always consumed together by Home's "Items Needing Attention"
 * card anyway. */
data class WardrobeAlerts(
    val forgotten: List<ForgottenGarment>,
    val overused: List<OverusedGarment>,
    val neverWorn: List<NeverWornGarment>,
)
