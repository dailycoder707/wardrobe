package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.sync.SyncPreferences
import kotlinx.coroutines.flow.Flow

interface SyncPreferencesRepository {
    fun observePreferences(): Flow<SyncPreferences>

    suspend fun setPreferences(preferences: SyncPreferences)
}
