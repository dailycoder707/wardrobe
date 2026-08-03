package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.ClosetPreferencesDataStore
import com.wardrobe.app.core.domain.repository.ClosetPreferencesRepository
import com.wardrobe.app.core.model.garment.GarmentSort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ClosetPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: ClosetPreferencesDataStore,
    ) : ClosetPreferencesRepository {
        override fun observeSort(): Flow<GarmentSort> = dataStore.observeSort()

        override suspend fun setSort(sort: GarmentSort) = dataStore.setSort(sort)

        override fun observeGridColumnCount(): Flow<Int> = dataStore.observeGridColumnCount()

        override suspend fun setGridColumnCount(count: Int) = dataStore.setGridColumnCount(count)

        override fun observeRecentSearches(): Flow<List<String>> = dataStore.observeRecentSearches()

        override suspend fun addRecentSearch(query: String) = dataStore.addRecentSearch(query)

        override suspend fun clearRecentSearches() = dataStore.clearRecentSearches()
    }
