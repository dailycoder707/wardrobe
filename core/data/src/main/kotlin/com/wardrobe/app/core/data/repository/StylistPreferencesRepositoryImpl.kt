package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.StylistPreferencesDataStore
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StylistPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: StylistPreferencesDataStore,
    ) : StylistPreferencesRepository {
        override fun observePreferences(): Flow<RecommendationPreferences> = dataStore.observePreferences()

        override suspend fun setPreferences(preferences: RecommendationPreferences) =
            dataStore.setPreferences(preferences)
    }
