package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

@Composable
fun ActiveFilterChipsRow(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
    onClearAll: () -> Unit,
) {
    val chips = buildActiveFilterChips(filters, options, onFiltersChange)

    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips) { (label, onRemove) ->
            WardrobeFilterChip(label = label, selected = true, onClick = onRemove)
        }
        item {
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }
}

private fun buildActiveFilterChips(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
): List<Pair<String, () -> Unit>> =
    listOfNotNull(
        filterChipEntry("Category", filters.category, options.categories, { it.id }, { it.name }) {
            onFiltersChange(filters.copy(category = null))
        },
        filterChipEntry("Color", filters.color, options.colors, { it.id }, { it.name }) {
            onFiltersChange(filters.copy(color = null))
        },
        filterChipEntry("Brand", filters.brand, options.brands, { it.id }, { it.name }) {
            onFiltersChange(filters.copy(brand = null))
        },
        filterChipEntry("Material", filters.material, options.materials, { it.id }, { it.name }) {
            onFiltersChange(filters.copy(material = null))
        },
        filterChipEntry("Tag", filters.tag, options.tags, { it.id }, { it.name }) {
            onFiltersChange(filters.copy(tag = null))
        },
        filters.season?.let { season ->
            "Season: ${season.name.lowercase().replaceFirstChar(Char::uppercase)}" to {
                onFiltersChange(filters.copy(season = null))
            }
        },
        filters.dressCode?.let { dressCode ->
            "Dress Code: ${dressCode.name.lowercase().replace('_', ' ')}" to {
                onFiltersChange(filters.copy(dressCode = null))
            }
        },
        if (filters.favoriteOnly) "Favorites" to { onFiltersChange(filters.copy(favoriteOnly = false)) } else null,
        if (filters.neverWorn) "Never worn" to { onFiltersChange(filters.copy(neverWorn = false)) } else null,
        if (filters.recentlyWornOnly) {
            "Recently worn" to { onFiltersChange(filters.copy(recentlyWornOnly = false)) }
        } else {
            null
        },
    )

private fun <T, Id> filterChipEntry(
    prefix: String,
    id: Id?,
    items: List<T>,
    idOf: (T) -> Id,
    nameOf: (T) -> String,
    onClear: () -> Unit,
): Pair<String, () -> Unit>? =
    id
        ?.let { wantedId -> items.firstOrNull { idOf(it) == wantedId } }
        ?.let { match -> "$prefix: ${nameOf(match)}" to onClear }
