package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.ui.components.DropdownField
import com.wardrobe.app.core.ui.components.MultiSelectChips

@Composable
internal fun GarmentReviewPreviewImage(
    previewImagePath: String?,
    usedOriginalFallback: Boolean,
) {
    if (previewImagePath == null) return
    Column {
        AsyncImage(
            model = previewImagePath,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        if (usedOriginalFallback) {
            Text(
                "Background removal wasn't able to process this photo — using the original.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun GarmentReviewBasicFields(
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
) {
    OutlinedTextField(
        value = form.name,
        onValueChange = { onFormChange(form.copy(name = it)) },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.size,
        onValueChange = { onFormChange(form.copy(size = it)) },
        label = { Text("Size") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.priceText,
        onValueChange = { onFormChange(form.copy(priceText = it)) },
        label = { Text("Purchase Price") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.purchaseDate,
        onValueChange = { onFormChange(form.copy(purchaseDate = it)) },
        label = { Text("Purchase Date (YYYY-MM-DD)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.careNotes,
        onValueChange = { onFormChange(form.copy(careNotes = it)) },
        label = { Text("Care Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    OutlinedTextField(
        value = form.notes,
        onValueChange = { onFormChange(form.copy(notes = it)) },
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@Composable
internal fun GarmentReviewDropdowns(
    state: GarmentReviewMetadataUiState,
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
) {
    DropdownField(
        label = "Brand",
        options = state.brands.map { it.id to it.name },
        selected = form.brandId,
        onSelect = { onFormChange(form.copy(brandId = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Color",
        options = state.colors.map { it.id to it.name },
        selected = form.primaryColorId,
        onSelect = { onFormChange(form.copy(primaryColorId = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Material",
        options = state.materials.map { it.id to it.name },
        selected = form.materialId,
        onSelect = { onFormChange(form.copy(materialId = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Condition",
        options = Condition.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = form.condition,
        onSelect = { onFormChange(form.copy(condition = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Availability",
        options = GarmentStatus.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = form.status,
        onSelect = { onFormChange(form.copy(status = it ?: GarmentStatus.ACTIVE)) },
    )
}

@Composable
internal fun GarmentReviewMultiSelectSections(
    state: GarmentReviewMetadataUiState,
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
) {
    MultiSelectChips(
        label = "Seasons",
        allOptions = Season.entries,
        selected = form.seasons,
        labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        onToggle = { season -> onFormChange(form.copy(seasons = toggled(form.seasons, season))) },
    )
    MultiSelectChips(
        label = "Dress Codes",
        allOptions = DressCode.entries,
        selected = form.dressCodes,
        labelFor = {
            it.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase)
        },
        onToggle = { dressCode -> onFormChange(form.copy(dressCodes = toggled(form.dressCodes, dressCode))) },
    )
    if (state.tags.isNotEmpty()) {
        MultiSelectChips(
            label = "Tags",
            allOptions = state.tags.map { it.id },
            selected = form.tagIds,
            labelFor = { tagId -> state.tags.first { it.id == tagId }.name },
            onToggle = { tagId -> onFormChange(form.copy(tagIds = toggled(form.tagIds, tagId))) },
        )
    }
}

@Composable
internal fun GarmentReviewToggles(
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
) {
    GarmentReviewToggleRow(
        label = "Favorite",
        checked = form.isFavorite,
        onCheckedChange = { onFormChange(form.copy(isFavorite = it)) },
    )
    GarmentReviewToggleRow(
        label = "In Laundry",
        checked = form.isInLaundry,
        onCheckedChange = { onFormChange(form.copy(isInLaundry = it)) },
    )
}

@Composable
private fun GarmentReviewToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun GarmentReviewSaveActions(
    canSave: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onSaveAsDraft: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, enabled = canSave && !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
        OutlinedButton(onClick = onSaveAsDraft, enabled = canSave && !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text("Save as Draft")
        }
        if (!canSave) {
            Text(
                "Choose a category to save this item.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
