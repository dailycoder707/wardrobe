package com.wardrobe.app.core.model.styling

import com.wardrobe.app.core.model.common.FeedbackId
import com.wardrobe.app.core.model.common.StyleRuleId
import java.time.Instant

enum class StyleRuleSourceType { USER_AUTHORED, DERIVED_FEEDBACK }

enum class StyleRuleType {
    AVOID_CATEGORY,
    ALWAYS_INCLUDE_CATEGORY,
    AVOID_COLOR_COMBO,
    MIN_WARMTH_BELOW_TEMP,
    AVOID_BRAND,
    CUSTOM,
}

/**
 * A persistent constraint on the styling engine (Phase 6). [parametersJson] is a JSON
 * blob rather than normalized columns — see phase-3-persistence.md's rationale (the
 * engine always loads all active rules and evaluates them in memory; nothing queries
 * rules by parameter value in SQL). Must always be traceable to the feedback that
 * created it when [sourceType] is [StyleRuleSourceType.DERIVED_FEEDBACK] — human-
 * readable, editable, deletable per the Constitution.
 */
data class StyleRule(
    val id: StyleRuleId,
    val description: String,
    val sourceType: StyleRuleSourceType,
    val sourceFeedbackId: FeedbackId?,
    val ruleType: StyleRuleType,
    val parametersJson: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
