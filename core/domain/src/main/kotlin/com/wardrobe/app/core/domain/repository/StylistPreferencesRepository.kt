package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.styling.RecommendationPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Persists the Stylist Preferences screen's settings (Phase 6) — DataStore-backed,
 * mirrors [ClosetPreferencesRepository]'s pattern exactly (one shared
 * `DataStore<Preferences>`, feature-scoped keys). The whole [RecommendationPreferences]
 * value is read/written together rather than field-by-field, since the preferences
 * screen edits and saves it as one form.
 */
interface StylistPreferencesRepository {
    fun observePreferences(): Flow<RecommendationPreferences>

    suspend fun setPreferences(preferences: RecommendationPreferences)
}
