package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.ui.components.MultiSelectChips
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

/** `docs/design/component-library.md`'s Bottom Sheet — a modal filter
 * builder covering every filterable facet a [com.wardrobe.app.core.model.garment.Garment]
 * genuinely carries (M17). Every section is multi-select ([MultiSelectChips]):
 * selections within one section are OR'd, sections are AND'd together (see
 * [matchesClosetFilters]). Applied live (each toggle updates
 * [onFiltersChange] immediately) — "Done" just dismisses, there's no
 * separate "Apply" step to forget to tap. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClosetFilterSheet(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilterSheetHeader(onClearAll)
            PresetsSection(filters, onFiltersChange)
            FilterSheetToggles(filters, onFiltersChange)
            FilterSheetChipSections(filters, options, onFiltersChange)
            PriceRangeSection(filters = filters, onFiltersChange = onFiltersChange)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun FilterSheetHeader(onClearAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Filters", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onClearAll) { Text("Clear all") }
    }
}

/** A "what are you dressing for" shortcut row — each preset just replaces
 * [ClosetFilterState.dressCodes], so it's an ordinary, removable filter, not
 * a separate mode (M17 Part 7J, see [ClosetFilterPreset]'s own doc comment
 * for why these are DressCode-based rather than Occasion-based). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsSection(
    filters: ClosetFilterState,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    FilterSection(title = "Quick filters") {
        ClosetFilterPreset.entries.forEach { preset ->
            WardrobeFilterChip(
                label = preset.label,
                selected = filters.dressCodes == preset.dressCodes,
                onClick = {
                    val next = if (filters.dressCodes == preset.dressCodes) emptySet() else preset.dressCodes
                    onFiltersChange(filters.copy(dressCodes = next))
                },
            )
        }
    }
}

@Composable
private fun FilterSheetToggles(
    filters: ClosetFilterState,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    ToggleRow("Favorites only", filters.favoriteOnly) { onFiltersChange(filters.copy(favoriteOnly = it)) }
    ToggleRow("Never worn", filters.neverWorn) { onFiltersChange(filters.copy(neverWorn = it)) }
    ToggleRow("Recently worn", filters.recentlyWornOnly) { onFiltersChange(filters.copy(recentlyWornOnly = it)) }
}

@Composable
private fun FilterSheetChipSections(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    ReferenceFacetSections(filters, options, onFiltersChange)
    EnumFacetSections(filters, onFiltersChange)
}

@Composable
private fun ReferenceFacetSections(
    filters: ClosetFilterState,
    options: ClosetFilterOptions,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    val categoriesById = options.categories.associateBy { it.id }
    MultiSelectChips(
        label = "Category",
        allOptions = groupCategoriesForFilterUi(options.categories),
        selected = options.categories.filterTo(mutableSetOf()) { it.id in filters.categories },
        labelFor = { category: Category -> category.displayLabel(categoriesById) },
        onToggle = { category: Category ->
            onFiltersChange(filters.copy(categories = filters.categories.toggled(category.id)))
        },
    )
    MultiSelectChips(
        label = "Color",
        allOptions = options.colors,
        selected = options.colors.filterTo(mutableSetOf()) { it.id in filters.colors },
        labelFor = { color: Color -> color.name },
        onToggle = { color: Color -> onFiltersChange(filters.copy(colors = filters.colors.toggled(color.id))) },
    )
    MultiSelectChips(
        label = "Brand",
        allOptions = options.brands,
        selected = options.brands.filterTo(mutableSetOf()) { it.id in filters.brands },
        labelFor = { brand: Brand -> brand.name },
        onToggle = { brand: Brand -> onFiltersChange(filters.copy(brands = filters.brands.toggled(brand.id))) },
    )
    MultiSelectChips(
        label = "Material",
        allOptions = options.materials,
        selected = options.materials.filterTo(mutableSetOf()) { it.id in filters.materials },
        labelFor = { material: Material -> material.name },
        onToggle = { material: Material ->
            onFiltersChange(filters.copy(materials = filters.materials.toggled(material.id)))
        },
    )
    MultiSelectChips(
        label = "Fabric",
        allOptions = options.fabrics,
        selected = options.fabrics.filterTo(mutableSetOf()) { it.id in filters.fabrics },
        labelFor = { fabric: Fabric -> fabric.name },
        onToggle = { fabric: Fabric -> onFiltersChange(filters.copy(fabrics = filters.fabrics.toggled(fabric.id))) },
    )
    MultiSelectChips(
        label = "Tag",
        allOptions = options.tags,
        selected = options.tags.filterTo(mutableSetOf()) { it.id in filters.tags },
        labelFor = { tag: Tag -> tag.name },
        onToggle = { tag: Tag -> onFiltersChange(filters.copy(tags = filters.tags.toggled(tag.id))) },
    )
    MultiSelectChips(
        label = "Occasion",
        allOptions = options.occasions,
        selected = options.occasions.filterTo(mutableSetOf()) { it.id in filters.occasions },
        labelFor = { occasion: Occasion -> occasion.name },
        onToggle = { occasion: Occasion ->
            onFiltersChange(filters.copy(occasions = filters.occasions.toggled(occasion.id)))
        },
    )
}

@Composable
private fun EnumFacetSections(
    filters: ClosetFilterState,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    MultiSelectChips(
        label = "Season",
        allOptions = Season.entries,
        selected = filters.seasons,
        labelFor = { season: Season -> season.displayLabel() },
        onToggle = { onFiltersChange(filters.copy(seasons = filters.seasons.toggled(it))) },
    )
    MultiSelectChips(
        label = "Dress Code",
        allOptions = DressCode.entries,
        selected = filters.dressCodes,
        labelFor = { dressCode: DressCode -> dressCode.displayLabel() },
        onToggle = { onFiltersChange(filters.copy(dressCodes = filters.dressCodes.toggled(it))) },
    )
    MultiSelectChips(
        label = "Fit",
        allOptions = Fit.entries,
        selected = filters.fits,
        labelFor = { fit: Fit -> fit.displayLabel() },
        onToggle = { onFiltersChange(filters.copy(fits = filters.fits.toggled(it))) },
    )
    MultiSelectChips(
        label = "Gender",
        allOptions = GarmentGender.entries,
        selected = filters.genders,
        labelFor = { gender: GarmentGender -> gender.displayLabel() },
        onToggle = { onFiltersChange(filters.copy(genders = filters.genders.toggled(it))) },
    )
    MultiSelectChips(
        label = "Waterproofing",
        allOptions = WaterproofLevel.entries,
        selected = filters.waterproofLevels,
        labelFor = { level: WaterproofLevel -> level.displayLabel() },
        onToggle = { onFiltersChange(filters.copy(waterproofLevels = filters.waterproofLevels.toggled(it))) },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun PriceRangeSection(
    filters: ClosetFilterState,
    onFiltersChange: (ClosetFilterState) -> Unit,
) {
    var minText by remember(filters.priceMin) { mutableStateOf(filters.priceMin?.toString().orEmpty()) }
    var maxText by remember(filters.priceMax) { mutableStateOf(filters.priceMax?.toString().orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Price range", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = {
                    minText = it
                    onFiltersChange(filters.copy(priceMin = it.toDoubleOrNull()))
                },
                label = { Text("Min") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = {
                    maxText = it
                    onFiltersChange(filters.copy(priceMax = it.toDoubleOrNull()))
                },
                label = { Text("Max") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}
