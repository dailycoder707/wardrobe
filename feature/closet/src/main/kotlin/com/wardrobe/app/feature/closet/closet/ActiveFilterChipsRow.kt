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

/** One chip per *selected value* (not per facet) — Category = [Tops, Denim]
 * shows two independently removable chips, matching M17 Part 7C's "[Black
 * x] [Winter x] [Work x]" example. */
private data class ActiveFilterChip(
    val key: String,
    val label: String,
    val remove: (ClosetFilterState) -> ClosetFilterState,
)

@Composable
fun ActiveFilterChipsRow(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
    onClearAll: () -> Unit,
) {
    val chips = buildActiveFilterChips(filters, options)

    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.key }) { chip ->
            WardrobeFilterChip(
                label = chip.label,
                selected = true,
                onClick = { onFiltersChange(chip.remove(filters)) },
                contentDescriptionOverride = "Remove ${chip.label} filter",
            )
        }
        item {
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }
}

private fun buildActiveFilterChips(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
): List<ActiveFilterChip> = referenceFacetChips(filters, options) + enumAndToggleFacetChips(filters)

private fun <Id> referenceFacetChip(
    prefix: String,
    id: Id,
    label: String,
    remove: (ClosetFilterState) -> ClosetFilterState,
) = ActiveFilterChip("$prefix-$id", "$prefix: $label", remove)

private fun referenceFacetChips(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
): List<ActiveFilterChip> {
    val categoriesById = options.categories.associateBy { it.id }

    fun <Id> chipsFor(
        prefix: String,
        selectedIds: Set<Id>,
        labelsById: Map<Id, String>,
        remove: (ClosetFilterState, Id) -> ClosetFilterState,
    ) = selectedIds.mapNotNull { id ->
        val label = labelsById[id] ?: return@mapNotNull null
        referenceFacetChip(prefix, id, label) { remove(it, id) }
    }

    return chipsFor(
        "Category",
        filters.categories,
        options.categories.associate {
            it.id to
                it.displayLabel(categoriesById)
        },
    ) { state, id ->
        state.copy(categories = state.categories - id)
    } +
        chipsFor("Color", filters.colors, options.colors.associate { it.id to it.name }) { state, id ->
            state.copy(colors = state.colors - id)
        } +
        chipsFor("Brand", filters.brands, options.brands.associate { it.id to it.name }) { state, id ->
            state.copy(brands = state.brands - id)
        } +
        chipsFor("Material", filters.materials, options.materials.associate { it.id to it.name }) { state, id ->
            state.copy(materials = state.materials - id)
        } +
        chipsFor("Fabric", filters.fabrics, options.fabrics.associate { it.id to it.name }) { state, id ->
            state.copy(fabrics = state.fabrics - id)
        } +
        chipsFor("Tag", filters.tags, options.tags.associate { it.id to it.name }) { state, id ->
            state.copy(tags = state.tags - id)
        } +
        chipsFor("Occasion", filters.occasions, options.occasions.associate { it.id to it.name }) { state, id ->
            state.copy(occasions = state.occasions - id)
        }
}

private fun enumAndToggleFacetChips(filters: ClosetFilterState): List<ActiveFilterChip> {
    val chips = mutableListOf<ActiveFilterChip>()

    filters.seasons.forEach { season ->
        chips +=
            ActiveFilterChip("season-$season", "Season: ${season.displayLabel()}") {
                it.copy(seasons = it.seasons - season)
            }
    }
    filters.dressCodes.forEach { dressCode ->
        chips +=
            ActiveFilterChip("dresscode-$dressCode", "Dress Code: ${dressCode.displayLabel()}") {
                it.copy(dressCodes = it.dressCodes - dressCode)
            }
    }
    filters.fits.forEach { fit ->
        chips += ActiveFilterChip("fit-$fit", "Fit: ${fit.displayLabel()}") { it.copy(fits = it.fits - fit) }
    }
    filters.genders.forEach { gender ->
        chips +=
            ActiveFilterChip("gender-$gender", "Gender: ${gender.displayLabel()}") {
                it.copy(genders = it.genders - gender)
            }
    }
    filters.waterproofLevels.forEach { level ->
        chips +=
            ActiveFilterChip("waterproof-$level", level.displayLabel()) {
                it.copy(waterproofLevels = it.waterproofLevels - level)
            }
    }
    if (filters.favoriteOnly) chips += ActiveFilterChip("favorite", "Favorites") { it.copy(favoriteOnly = false) }
    if (filters.neverWorn) chips += ActiveFilterChip("neverworn", "Never worn") { it.copy(neverWorn = false) }
    if (filters.recentlyWornOnly) {
        chips += ActiveFilterChip("recentlyworn", "Recently worn") { it.copy(recentlyWornOnly = false) }
    }
    filters.priceMin?.let { min -> chips += ActiveFilterChip("pricemin", "Min: $min") { it.copy(priceMin = null) } }
    filters.priceMax?.let { max -> chips += ActiveFilterChip("pricemax", "Max: $max") { it.copy(priceMax = null) } }

    return chips
}
