package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.ui.components.DropdownField
import com.wardrobe.app.core.ui.components.MultiSelectChips

private fun <T : Enum<T>> T.titleCase(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

/** M22 — Part 4's "AI-generated vs. user-entered information is
 * distinguishable" requirement, applied where it was actually missing: the
 * "Detected attributes" summary above already distinguishes them, but once
 * a value is bound into the real editable control below, it previously
 * looked pixel-identical whether AI-suggested or hand-typed. Reuses
 * [isSuggestionApplied] (the exact same "is the field's current value this
 * suggestion's value" check the summary section already uses) rather than
 * inventing a second notion of "applied." Returns `null` — no caption at
 * all — the instant a user edits the field away from the suggestion, since
 * `isSuggestionApplied` re-evaluates against the live form value every time. */
private fun aiSuggestedHelperText(
    field: MetadataField,
    suggestions: List<MetadataSuggestion>,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
): String? =
    suggestions
        .filter { it.field == field }
        .firstOrNull { isSuggestionApplied(form, it.field, it.value, reference) }
        ?.let { "AI suggested" }

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

/** M10 addition — [MetadataField.PATTERN]/[MetadataField.FIT]/
 * [MetadataField.SLEEVE_LENGTH]/[MetadataField.LENGTH] never had a form
 * field in v1; the AI metadata engine can suggest all four so the review
 * screen needs somewhere to show and edit them. */
@Composable
internal fun GarmentReviewGarmentAttributeFields(
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
    suggestions: List<MetadataSuggestion> = emptyList(),
    reference: ReviewReferenceData? = null,
) {
    fun helperFor(field: MetadataField): String? =
        reference?.let { aiSuggestedHelperText(field, suggestions, form, it) }

    OutlinedTextField(
        value = form.patternText,
        onValueChange = { onFormChange(form.copy(patternText = it)) },
        label = { Text("Pattern") },
        supportingText = helperFor(MetadataField.PATTERN)?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    DropdownField(
        label = "Fit",
        options = Fit.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = form.fit,
        onSelect = { onFormChange(form.copy(fit = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.FIT),
    )
    DropdownField(
        label = "Sleeve Length",
        options =
            SleeveLength.entries.map {
                it to
                    it.name
                        .lowercase()
                        .replace('_', ' ')
                        .replaceFirstChar(Char::uppercase)
            },
        selected = form.sleeveLength,
        onSelect = { onFormChange(form.copy(sleeveLength = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.SLEEVE_LENGTH),
    )
    DropdownField(
        label = "Length",
        options = GarmentLength.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = form.length,
        onSelect = { onFormChange(form.copy(length = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.LENGTH),
    )
    GarmentReviewMoreAttributeFields(form, onFormChange, suggestions, reference)
}

/** Split out of [GarmentReviewGarmentAttributeFields] purely to stay under
 * detekt's `LongMethod` threshold (M22) — the remaining Neckline/Gender/
 * Waterproof Level fields, same "AI suggested" helper-text wiring. */
@Composable
private fun GarmentReviewMoreAttributeFields(
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
    suggestions: List<MetadataSuggestion>,
    reference: ReviewReferenceData?,
) {
    fun helperFor(field: MetadataField): String? =
        reference?.let { aiSuggestedHelperText(field, suggestions, form, it) }

    DropdownField(
        label = "Neckline",
        options = Neckline.entries.map { it to it.titleCase() },
        selected = form.neckline,
        onSelect = { onFormChange(form.copy(neckline = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.NECKLINE),
    )
    DropdownField(
        label = "Gender",
        options = GarmentGender.entries.map { it to it.titleCase() },
        selected = form.gender,
        onSelect = { onFormChange(form.copy(gender = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.GENDER),
    )
    DropdownField(
        label = "Waterproof Level",
        options = WaterproofLevel.entries.map { it to it.titleCase() },
        selected = form.waterproofLevel,
        onSelect = { onFormChange(form.copy(waterproofLevel = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.WATERPROOF_LEVEL),
    )
}

@Composable
internal fun GarmentReviewDropdowns(
    state: GarmentReviewMetadataUiState,
    form: GarmentMetadataFormState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
    reference: ReviewReferenceData? = null,
) {
    fun helperFor(field: MetadataField): String? =
        reference?.let { aiSuggestedHelperText(field, state.metadataSuggestions, form, it) }

    DropdownField(
        label = "Brand",
        options = state.brands.map { it.id to it.name },
        selected = form.brandId,
        onSelect = { onFormChange(form.copy(brandId = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.BRAND),
    )
    DropdownField(
        label = "Color",
        options = state.colors.map { it.id to it.name },
        selected = form.primaryColorId,
        onSelect = { onFormChange(form.copy(primaryColorId = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.PRIMARY_COLOR),
    )
    DropdownField(
        label = "Secondary Color",
        options = state.colors.map { it.id to it.name },
        selected = form.secondaryColorId,
        onSelect = { onFormChange(form.copy(secondaryColorId = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.SECONDARY_COLOR),
    )
    DropdownField(
        label = "Material",
        options = state.materials.map { it.id to it.name },
        selected = form.materialId,
        onSelect = { onFormChange(form.copy(materialId = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.MATERIAL),
    )
    DropdownField(
        label = "Fabric",
        options = state.fabrics.map { it.id to it.name },
        selected = form.fabricId,
        onSelect = { onFormChange(form.copy(fabricId = it)) },
        allowNone = true,
        helperText = helperFor(MetadataField.FABRIC),
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
    if (state.occasions.isNotEmpty()) {
        MultiSelectChips(
            label = "Occasions",
            allOptions = state.occasions.map { it.id },
            selected = form.occasionIds,
            labelFor = { occasionId -> state.occasions.first { it.id == occasionId }.name },
            onToggle = { occasionId -> onFormChange(form.copy(occasionIds = toggled(form.occasionIds, occasionId))) },
        )
    }
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
