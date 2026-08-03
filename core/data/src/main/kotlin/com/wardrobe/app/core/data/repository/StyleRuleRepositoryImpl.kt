package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.FeedbackDao
import com.wardrobe.app.core.database.dao.StyleRuleDao
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.model.common.StyleRuleId
import com.wardrobe.app.core.model.styling.Feedback
import com.wardrobe.app.core.model.styling.StyleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StyleRuleRepositoryImpl
    @Inject
    constructor(
        private val styleRuleDao: StyleRuleDao,
        private val feedbackDao: FeedbackDao,
    ) : StyleRuleRepository {
        override fun observeActiveRules(): Flow<List<StyleRule>> =
            styleRuleDao.observeActive().map { rows -> rows.map { it.toDomain() } }

        /**
         * Persists the feedback and returns `null` — deriving a [StyleRule] from
         * feedback is Phase 6's styling engine's job, not this repository's
         * (`phase-3-persistence.md`'s `StyleRule` KDoc). This is real, complete
         * persistence work, not a stub: the feedback row is genuinely saved and
         * queryable (`FeedbackDao.getByGeneratedStyleRule`) the moment Phase 6 exists to
         * derive something from it.
         */
        override suspend fun recordFeedback(feedback: Feedback): StyleRule? {
            val now = System.currentTimeMillis()
            feedbackDao.insert(
                feedback.toEntity().copy(
                    id = 0,
                    syncId =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    updatedAt = now,
                ),
            )
            return null
        }

        override suspend fun addUserRule(rule: StyleRule) {
            val now = System.currentTimeMillis()
            styleRuleDao.insert(
                rule.toEntity().copy(
                    id = 0,
                    syncId =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    updatedAt = now,
                ),
            )
        }

        override suspend fun deleteRule(id: StyleRuleId) = styleRuleDao.deleteById(id.value)
    }
