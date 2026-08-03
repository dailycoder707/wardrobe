package com.wardrobe.app.feature.closet.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GarmentDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteBlockedMessage by viewModel.deleteBlockedMessage.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GarmentDetailTopBar(
                title = state.garment?.name ?: "Garment",
                garment = state.garment,
                onBack = onBack,
                onToggleFavorite = viewModel::onToggleFavorite,
                onEdit = onEdit,
                onDeleteRequest = { showDeleteConfirmation = true },
            )
        },
    ) { innerPadding ->
        GarmentDetailScreenBody(
            state = state,
            innerPadding = innerPadding,
            onToggleLaundry = viewModel::onToggleLaundry,
        )
    }

    GarmentDetailDialogs(
        showDeleteConfirmation = showDeleteConfirmation,
        onDismissDeleteConfirmation = { showDeleteConfirmation = false },
        onConfirmDelete = {
            showDeleteConfirmation = false
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "Item deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                // Nothing was actually removed yet — Undo just means "don't call
                // onDelete", not "re-insert" (matches ClosetSelectionController's
                // same deferred-real-delete pattern for the multi-select case).
                if (result != SnackbarResult.ActionPerformed) {
                    viewModel.onDelete(onDeleted = onBack)
                }
            }
        },
        deleteBlockedMessage = deleteBlockedMessage,
        onDeleteBlockedMessageShown = viewModel::onDeleteBlockedMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarmentDetailTopBar(
    title: String,
    garment: Garment?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
            if (garment != null) {
                GarmentDetailActions(garment, onToggleFavorite, onEdit, onDeleteRequest)
            }
        },
    )
}

@Composable
private fun GarmentDetailActions(
    garment: Garment,
    onToggleFavorite: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val favoriteTint =
        if (garment.isFavorite) WardrobeTheme.extendedColors.accent else MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onToggleFavorite) {
        Icon(
            imageVector = if (garment.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (garment.isFavorite) "Remove from favorites" else "Add to favorites",
            tint = favoriteTint,
        )
    }
    IconButton(onClick = { onEdit(garment.id.value) }) {
        Icon(Icons.Filled.Edit, contentDescription = "Edit")
    }
    IconButton(onClick = onDeleteRequest) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete")
    }
}

@Composable
private fun GarmentDetailScreenBody(
    state: GarmentDetailUiState,
    innerPadding: PaddingValues,
    onToggleLaundry: () -> Unit,
) {
    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.notFound -> {
            EmptyState(
                icon = Icons.Filled.Delete,
                headline = "Item not found",
                supportingText = "This item may have been deleted.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }

        state.error != null -> {
            EmptyState(
                icon = Icons.Filled.Delete,
                headline = "Something went wrong",
                supportingText = state.error.orEmpty(),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }

        else -> {
            GarmentDetailContent(
                state = state,
                onToggleLaundry = onToggleLaundry,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun GarmentDetailDialogs(
    showDeleteConfirmation: Boolean,
    onDismissDeleteConfirmation: () -> Unit,
    onConfirmDelete: () -> Unit,
    deleteBlockedMessage: String?,
    onDeleteBlockedMessageShown: () -> Unit,
) {
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text("Delete this item?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismissDeleteConfirmation) { Text("Cancel") } },
        )
    }

    if (deleteBlockedMessage != null) {
        AlertDialog(
            onDismissRequest = onDeleteBlockedMessageShown,
            title = { Text("Can't delete") },
            text = { Text(deleteBlockedMessage) },
            confirmButton = { TextButton(onClick = onDeleteBlockedMessageShown) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GarmentDetailContent(
    state: GarmentDetailUiState,
    onToggleLaundry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val garment = state.garment ?: return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                ImageGallery(images = garment.images, modifier = Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                ) {
                    GarmentMetadata(state, onToggleLaundry)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ImageGallery(images = garment.images, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Column(modifier = Modifier.padding(24.dp)) {
                    GarmentMetadata(state, onToggleLaundry)
                }
            }
        }
    }
}
