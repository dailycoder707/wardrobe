package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentSortField
import com.wardrobe.app.core.model.garment.SortDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_GRID_COLUMN_COUNT = 3

// A single delimited string rather than pulling in kotlinx.serialization for one
// small list — U+001F (ASCII "unit separator") is chosen specifically because
// it cannot appear in a search query typed on a soft keyboard, so no escaping
// logic is needed.
private const val RECENT_SEARCH_SEPARATOR = "\u001F"
private const val MAX_RECENT_SEARCHES = 8

@Singleton
class ClosetPreferencesDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observeSort(): Flow<GarmentSort> =
            dataStore.data.map { prefs ->
                val field =
                    prefs[PreferenceKeys.CLOSET_SORT_FIELD]?.let {
                        runCatching { GarmentSortField.valueOf(it) }.getOrNull()
                    } ?: GarmentSort.DEFAULT.field
                val direction =
                    prefs[PreferenceKeys.CLOSET_SORT_DIRECTION]?.let {
                        runCatching { SortDirection.valueOf(it) }.getOrNull()
                    } ?: GarmentSort.DEFAULT.direction
                GarmentSort(field, direction)
            }

        suspend fun setSort(sort: GarmentSort) {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.CLOSET_SORT_FIELD] = sort.field.name
                prefs[PreferenceKeys.CLOSET_SORT_DIRECTION] = sort.direction.name
            }
        }

        fun observeGridColumnCount(): Flow<Int> =
            dataStore.data.map { prefs -> prefs[PreferenceKeys.CLOSET_GRID_COLUMN_COUNT] ?: DEFAULT_GRID_COLUMN_COUNT }

        suspend fun setGridColumnCount(count: Int) {
            dataStore.edit { prefs -> prefs[PreferenceKeys.CLOSET_GRID_COLUMN_COUNT] = count }
        }

        fun observeRecentSearches(): Flow<List<String>> =
            dataStore.data.map { prefs -> decodeRecentSearches(prefs[PreferenceKeys.CLOSET_RECENT_SEARCHES]) }

        suspend fun addRecentSearch(query: String) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return
            dataStore.edit { prefs ->
                val current = decodeRecentSearches(prefs[PreferenceKeys.CLOSET_RECENT_SEARCHES])
                val updated =
                    (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                        .take(MAX_RECENT_SEARCHES)
                prefs[PreferenceKeys.CLOSET_RECENT_SEARCHES] = updated.joinToString(RECENT_SEARCH_SEPARATOR)
            }
        }

        suspend fun clearRecentSearches() {
            dataStore.edit { prefs -> prefs.remove(PreferenceKeys.CLOSET_RECENT_SEARCHES) }
        }

        private fun decodeRecentSearches(raw: String?): List<String> =
            raw?.split(RECENT_SEARCH_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }
