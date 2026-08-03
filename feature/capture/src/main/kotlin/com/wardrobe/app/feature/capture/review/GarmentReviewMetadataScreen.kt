package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.wardrobe.app.core.ui.components.CategoryPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentReviewMetadataScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarmentReviewMetadataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.didSave, state.needsRestage) {
        if (state.didSave || state.needsRestage) onDone()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add Details") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            GarmentReviewMetadataForm(
                state = state,
                onFormChange = viewModel::onFormChange,
                onSave = viewModel::onSave,
                onSaveAsDraft = viewModel::onSaveAsDraft,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun GarmentReviewMetadataForm(
    state: GarmentReviewMetadataUiState,
    onFormChange: (GarmentMetadataFormState) -> Unit,
    onSave: () -> Unit,
    onSaveAsDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GarmentReviewPreviewImage(state.previewImagePath, state.usedOriginalFallback)
        GarmentReviewDuplicateBanners(state)

        state.saveError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        CategoryPicker(
            categories = state.categories,
            selectedCategoryId = form.categoryId,
            onSelect = { onFormChange(form.copy(categoryId = it)) },
        )

        GarmentReviewBasicFields(form, onFormChange)
        GarmentReviewDropdowns(state, form, onFormChange)
        GarmentReviewMultiSelectSections(state, form, onFormChange)
        GarmentReviewToggles(form, onFormChange)
        GarmentReviewSaveActions(
            canSave = form.categoryId != null,
            isSaving = state.isSaving,
            onSave = onSave,
            onSaveAsDraft = onSaveAsDraft,
        )
    }
}

@Composable
private fun GarmentReviewDuplicateBanners(state: GarmentReviewMetadataUiState) {
    state.checksumDuplicateGarmentName?.let {
        Text(
            "You've already imported this exact photo: \"$it\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.potentialDuplicates.isNotEmpty()) {
        Text(
            "This looks similar to what's already in your wardrobe: " +
                state.potentialDuplicates.joinToString { it.name ?: "an item" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun <T> toggled(
    set: Set<T>,
    value: T,
): Set<T> = if (value in set) set - value else set + value
