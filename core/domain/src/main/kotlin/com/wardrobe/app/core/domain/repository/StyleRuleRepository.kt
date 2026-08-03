package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.StyleRuleId
import com.wardrobe.app.core.model.styling.Feedback
import com.wardrobe.app.core.model.styling.StyleRule
import kotlinx.coroutines.flow.Flow

interface StyleRuleRepository {
    fun observeActiveRules(): Flow<List<StyleRule>>

    /** Records the feedback and, when warranted, derives and persists a new
     * [StyleRule] traceable back to it (Constitution: every derived rule must be
     * traceable to the feedback that created it) — the derivation logic itself is
     * Phase 6's styling engine, not this repository. Returns the derived rule, if any. */
    suspend fun recordFeedback(feedback: Feedback): StyleRule?

    suspend fun addUserRule(rule: StyleRule)

    suspend fun deleteRule(id: StyleRuleId)
}
