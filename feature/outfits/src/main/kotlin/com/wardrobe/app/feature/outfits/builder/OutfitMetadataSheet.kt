package com.wardrobe.app.feature.outfits.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitMetadataSheet(
    form: OutfitBuilderFormState,
    reference: OutfitBuilderReferenceData,
    onFormChange: (OutfitBuilderFormState) -> Unit,
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
            Text("Look details", style = MaterialTheme.typography.titleLarge)
            OutfitIdentityFields(form = form, reference = reference, onFormChange = onFormChange)
            OutfitMetadataChips(form = form, reference = reference, onFormChange = onFormChange)
            OutfitMoodNotesAndFavorite(form = form, onFormChange = onFormChange)
        }
    }
}

@Composable
private fun OutfitIdentityFields(
    form: OutfitBuilderFormState,
    reference: OutfitBuilderReferenceData,
    onFormChange: (OutfitBuilderFormState) -> Unit,
) {
    OutlinedTextField(
        value = form.name,
        onValueChange = { onFormChange(form.copy(name = it)) },
        label = { Text("Name") },
        placeholder = { Text("Weekend Brunch") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    OccasionDropdown(
        occasions = reference.occasions.map { it.id to it.name },
        selected = form.occasionId,
        onSelect = { onFormChange(form.copy(occasionId = it)) },
    )
}

@Composable
private fun OutfitMetadataChips(
    form: OutfitBuilderFormState,
    reference: OutfitBuilderReferenceData,
    onFormChange: (OutfitBuilderFormState) -> Unit,
) {
    MetadataChipSection(
        label = "Season",
        items = Season.entries,
        selected = form.seasons,
        itemLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        onToggle = { season ->
            onFormChange(form.copy(seasons = toggled(form.seasons, season)))
        },
    )

    MetadataChipSection(
        label = "Dress Code",
        items = DressCode.entries,
        selected = form.dressCodes,
        itemLabel = {
            it.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase)
        },
        onToggle = { dressCode ->
            onFormChange(form.copy(dressCodes = toggled(form.dressCodes, dressCode)))
        },
    )

    if (reference.tags.isNotEmpty()) {
        MetadataChipSection(
            label = "Tags",
            items = reference.tags.map { it.id },
            selected = form.tagIds,
            itemLabel = { tagId -> reference.tags.first { it.id == tagId }.name },
            onToggle = { tagId -> onFormChange(form.copy(tagIds = toggled(form.tagIds, tagId))) },
        )
    }
}

@Composable
private fun OutfitMoodNotesAndFavorite(
    form: OutfitBuilderFormState,
    onFormChange: (OutfitBuilderFormState) -> Unit,
) {
    OutlinedTextField(
        value = form.mood,
        onValueChange = { onFormChange(form.copy(mood = it)) },
        label = { Text("Mood") },
        placeholder = { Text("Confident, relaxed, playful…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    OutlinedTextField(
        value = form.notes,
        onValueChange = { onFormChange(form.copy(notes = it)) },
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Favorite", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = form.isFavorite, onCheckedChange = { onFormChange(form.copy(isFavorite = it)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccasionDropdown(
    occasions: List<Pair<OccasionId, String>>,
    selected: OccasionId?,
    onSelect: (OccasionId?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = occasions.firstOrNull { it.first == selected }?.second.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Occasion") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = {
                onSelect(null)
                expanded = false
            })
            occasions.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MetadataChipSection(
    label: String,
    items: List<T>,
    selected: Set<T>,
    itemLabel: (T) -> String,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                WardrobeFilterChip(
                    label = itemLabel(item),
                    selected = item in selected,
                    onClick = { onToggle(item) },
                )
            }
        }
    }
}

private fun <T> toggled(
    set: Set<T>,
    value: T,
): Set<T> = if (value in set) set - value else set + value
