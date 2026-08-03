package com.wardrobe.app.feature.closet.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.ui.components.DropdownField
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.MultiSelectChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGarmentScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditGarmentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.didSave) {
        if (state.didSave) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel") }
                },
                actions = {
                    Button(onClick = viewModel::onSave, enabled = !state.isSaving) { Text("Save") }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.notFound -> {
                EmptyState(
                    icon = Icons.Filled.ArrowBack,
                    headline = "Item not found",
                    supportingText = "This item may have been deleted.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> {
                EditGarmentForm(
                    state = state,
                    onFormChange = viewModel::onFormChange,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGarmentForm(
    state: EditGarmentUiState,
    onFormChange: (EditGarmentFormState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.saveError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        EditGarmentBasicFields(form, onFormChange)
        EditGarmentDropdowns(state, form, onFormChange)
        EditGarmentMultiSelectSections(state, form, onFormChange)

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
            minLines = 3,
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }
}

@Composable
private fun EditGarmentBasicFields(
    form: EditGarmentFormState,
    onFormChange: (EditGarmentFormState) -> Unit,
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
        label = { Text("Price") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGarmentDropdowns(
    state: EditGarmentUiState,
    form: EditGarmentFormState,
    onFormChange: (EditGarmentFormState) -> Unit,
) {
    DropdownField(
        label = "Category",
        options = state.categories.map { it.id to it.name },
        selected = form.categoryId,
        onSelect = { onFormChange(form.copy(categoryId = it)) },
    )
    DropdownField(
        label = "Brand",
        options = state.brands.map { it.id to it.name },
        selected = form.brandId,
        onSelect = { onFormChange(form.copy(brandId = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Primary Color",
        options = state.colors.map { it.id to it.name },
        selected = form.primaryColorId,
        onSelect = { onFormChange(form.copy(primaryColorId = it)) },
        allowNone = true,
    )
    DropdownField(
        label = "Condition",
        options = Condition.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = form.condition,
        onSelect = { onFormChange(form.copy(condition = it)) },
        allowNone = true,
    )
}

@Composable
private fun EditGarmentMultiSelectSections(
    state: EditGarmentUiState,
    form: EditGarmentFormState,
    onFormChange: (EditGarmentFormState) -> Unit,
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

private fun <T> toggled(
    set: Set<T>,
    value: T,
): Set<T> = if (value in set) set - value else set + value
