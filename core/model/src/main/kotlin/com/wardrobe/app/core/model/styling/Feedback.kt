package com.wardrobe.app.core.model.styling

import com.wardrobe.app.core.model.common.FeedbackId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.StyleRuleId
import java.time.Instant

enum class FeedbackTargetType { GARMENT, OUTFIT }

enum class FeedbackVote { UP, DOWN }

/** Thumbs up/down on a suggestion — the training signal for [StyleRule] derivation. */
data class Feedback(
    val id: FeedbackId,
    val targetType: FeedbackTargetType,
    val targetGarmentId: GarmentId?,
    val targetOutfitId: OutfitId?,
    val vote: FeedbackVote,
    val reasonCode: String?,
    val reasonText: String?,
    val generatedStyleRuleId: StyleRuleId?,
    val createdAt: Instant,
) {
    init {
        require((targetGarmentId == null) != (targetOutfitId == null)) {
            "exactly one of targetGarmentId/targetOutfitId must be set"
        }
    }
}
