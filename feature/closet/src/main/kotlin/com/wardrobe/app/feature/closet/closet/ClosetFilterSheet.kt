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
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

/** `docs/design/component-library.md`'s Bottom Sheet — a modal filter builder
 * covering every facet the master prompt asks for: category, color, brand,
 * material, season, dress code, tag, favorite, never/recently worn, price
 * range. Applied live (each toggle updates [onFiltersChange] immediately) —
 * "Done" just dismisses, there's no separate "Apply" step to forget to tap. */
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
    SingleSelectFilterSection(
        title = "Category",
        items = options.categories,
        isSelected = { filters.category == it.id },
        label = { it.name },
        onToggle = { onFiltersChange(filters.copy(category = toggled(filters.category, it.id))) },
    )
    SingleSelectFilterSection(
        title = "Color",
        items = options.colors,
        isSelected = { filters.color == it.id },
        label = { it.name },
        onToggle = { onFiltersChange(filters.copy(color = toggled(filters.color, it.id))) },
    )
    SingleSelectFilterSection(
        title = "Brand",
        items = options.brands,
        isSelected = { filters.brand == it.id },
        label = { it.name },
        onToggle = { onFiltersChange(filters.copy(brand = toggled(filters.brand, it.id))) },
    )
    SingleSelectFilterSection(
        title = "Material",
        items = options.materials,
        isSelected = { filters.material == it.id },
        label = { it.name },
        onToggle = { onFiltersChange(filters.copy(material = toggled(filters.material, it.id))) },
    )
    SingleSelectFilterSection(
        title = "Tag",
        items = options.tags,
        isSelected = { filters.tag == it.id },
        label = { it.name },
        onToggle = { onFiltersChange(filters.copy(tag = toggled(filters.tag, it.id))) },
    )
    SingleSelectFilterSection(
        title = "Season",
        items = Season.entries,
        isSelected = { filters.season == it },
        label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        onToggle = { onFiltersChange(filters.copy(season = toggled(filters.season, it))) },
    )
    SingleSelectFilterSection(
        title = "Dress Code",
        items = DressCode.entries,
        isSelected = { filters.dressCode == it },
        label = {
            it.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase)
        },
        onToggle = { onFiltersChange(filters.copy(dressCode = toggled(filters.dressCode, it))) },
    )
}

/** Single-select-with-deselect toggle: tapping the already-selected value clears it. */
private fun <T> toggled(
    current: T?,
    value: T,
): T? = if (current == value) null else value

@Composable
private fun <T> SingleSelectFilterSection(
    title: String,
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    FilterSection(title = title) {
        items.forEach { item ->
            WardrobeFilterChip(
                label = label(item),
                selected = isSelected(item),
                onClick = { onToggle(item) },
            )
        }
    }
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
