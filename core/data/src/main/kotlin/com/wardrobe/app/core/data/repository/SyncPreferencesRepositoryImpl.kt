package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.SyncPreferencesDataStore
import com.wardrobe.app.core.domain.repository.SyncPreferencesRepository
import com.wardrobe.app.core.model.sync.SyncPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SyncPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: SyncPreferencesDataStore,
    ) : SyncPreferencesRepository {
        override fun observePreferences(): Flow<SyncPreferences> = dataStore.observePreferences()

        override suspend fun setPreferences(preferences: SyncPreferences) = dataStore.setPreferences(preferences)
    }
