package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.garment.GarmentSort
import kotlinx.coroutines.flow.Flow

/**
 * Persists the Closet screen's sort order and grid density (Phase 5c —
 * `component-library.md`'s Grid Density Control requires both the pinch
 * gesture and the density stepper to "produce the identical result," which
 * this single persisted value backs regardless of which control changed it).
 */
interface ClosetPreferencesRepository {
    fun observeSort(): Flow<GarmentSort>

    suspend fun setSort(sort: GarmentSort)

    fun observeGridColumnCount(): Flow<Int>

    suspend fun setGridColumnCount(count: Int)

    /** Most-recent-first, capped — backs Search's "recent searches" list. */
    fun observeRecentSearches(): Flow<List<String>>

    suspend fun addRecentSearch(query: String)

    suspend fun clearRecentSearches()
}
