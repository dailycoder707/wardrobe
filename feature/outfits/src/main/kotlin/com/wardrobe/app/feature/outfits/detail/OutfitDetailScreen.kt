package com.wardrobe.app.feature.outfits.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class OutfitDetailTopBarState(
    val title: String,
    val isFavorite: Boolean,
    val isArchived: Boolean,
)

internal data class OutfitDetailActions(
    val onBack: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onRestyle: () -> Unit,
    val onTryOn: () -> Unit,
    val onDuplicate: () -> Unit,
    val onArchiveToggle: () -> Unit,
    val onDeleteRequest: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(
    onBack: () -> Unit,
    onRestyle: () -> Unit,
    onTryOn: () -> Unit,
    onDuplicated: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutfitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteBlockedMessage by viewModel.deleteBlockedMessage.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val actions =
        OutfitDetailActions(
            onBack = onBack,
            onToggleFavorite = viewModel::onToggleFavorite,
            onRestyle = onRestyle,
            onTryOn = onTryOn,
            onDuplicate = { viewModel.onDuplicate(onDuplicated) },
            onArchiveToggle = { viewModel.onArchive(!state.isArchived) },
            onDeleteRequest = { showDeleteConfirmation = true },
        )

    Scaffold(
        modifier = modifier,
        topBar = {
            OutfitDetailTopBar(
                state = OutfitDetailTopBarState(state.title, state.isFavorite, state.isArchived),
                actions = actions,
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
                    icon = Icons.Filled.Delete,
                    headline = "Look not found",
                    supportingText = "This look may have been deleted.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> {
                OutfitDetailContent(state = state, modifier = Modifier.padding(innerPadding))
            }
        }
    }

    OutfitDetailDialogs(
        showDeleteConfirmation = showDeleteConfirmation,
        onDeleteConfirmationDismissed = { showDeleteConfirmation = false },
        onConfirmDelete = {
            showDeleteConfirmation = false
            viewModel.onDelete(onDeleted = onBack)
        },
        deleteBlockedMessage = deleteBlockedMessage,
        onDeleteBlockedMessageShown = viewModel::onDeleteBlockedMessageShown,
    )
}

@Composable
private fun OutfitDetailDialogs(
    showDeleteConfirmation: Boolean,
    onDeleteConfirmationDismissed: () -> Unit,
    onConfirmDelete: () -> Unit,
    deleteBlockedMessage: String?,
    onDeleteBlockedMessageShown: () -> Unit,
) {
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDeleteConfirmationDismissed,
            title = { Text("Delete this look?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDeleteConfirmationDismissed) { Text("Cancel") }
            },
        )
    }

    if (deleteBlockedMessage != null) {
        AlertDialog(
            onDismissRequest = onDeleteBlockedMessageShown,
            title = { Text("Can't delete") },
            text = { Text(deleteBlockedMessage) },
            confirmButton = {
                TextButton(onClick = onDeleteBlockedMessageShown) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitDetailTopBar(
    state: OutfitDetailTopBarState,
    actions: OutfitDetailActions,
) {
    TopAppBar(
        title = { Text(state.title.ifBlank { "Look" }) },
        navigationIcon = {
            IconButton(onClick = actions.onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
            val favoriteTint =
                if (state.isFavorite) WardrobeTheme.extendedColors.accent else MaterialTheme.colorScheme.onSurface
            IconButton(onClick = actions.onToggleFavorite) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = favoriteTint,
                )
            }
            IconButton(onClick = actions.onRestyle) {
                Icon(Icons.Filled.Edit, contentDescription = "Restyle")
            }
            IconButton(onClick = actions.onTryOn) {
                Icon(Icons.Filled.Checkroom, contentDescription = "Try On")
            }
            IconButton(onClick = actions.onDuplicate) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = actions.onArchiveToggle) {
                Icon(Icons.Outlined.Archive, contentDescription = if (state.isArchived) "Unarchive" else "Archive")
            }
            IconButton(onClick = actions.onDeleteRequest) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
    )
}

@Composable
private fun OutfitDetailContent(
    state: OutfitDetailUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutfitSlotGrid(state.slots)

        OutfitMetadataSection(state)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock(label = "Wear Count", value = state.wearCount.toString())
            StatBlock(
                label = "Last Worn",
                value = state.lastWornDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Never",
            )
        }

        state.insights?.let { OutfitInsightsSection(it) }

        if (state.wearHistory.isNotEmpty()) {
            WearHistorySection(state.wearHistory)
        }
    }
}

private const val SLOT_GRID_COLUMNS = 3

/** A plain chunked grid, not `LazyVerticalGrid` — at most nine slots, and
 * this content already lives inside the screen's own vertical scroll, where
 * an unbounded-height lazy layout would crash on measurement. */
@Composable
private fun OutfitSlotGrid(slots: List<OutfitSlotUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.chunked(SLOT_GRID_COLUMNS).forEach { rowSlots ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowSlots.forEach { slotModel ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
                        AsyncImage(
                            model = slotModel.garment.thumbnailPath,
                            contentDescription = slotModel.garment.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                repeat(SLOT_GRID_COLUMNS - rowSlots.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OutfitMetadataSection(state: OutfitDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.occasionName != null) MetadataRow("Occasion", state.occasionName)
        if (!state.mood.isNullOrBlank()) MetadataRow("Mood", state.mood)
        if (state.seasonNames.isNotEmpty()) ChipRow("Season", state.seasonNames)
        if (state.dressCodeNames.isNotEmpty()) ChipRow("Dress Code", state.dressCodeNames)
        if (state.tagNames.isNotEmpty()) ChipRow("Tags", state.tagNames)
        if (!state.notes.isNullOrBlank()) {
            Column {
                Text("Notes", style = MaterialTheme.typography.titleMedium)
                Text(state.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun WearHistorySection(history: List<LocalDate>) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    Column {
        Text("Wear History", style = MaterialTheme.typography.titleMedium)
        history.take(MAX_HISTORY_ROWS).forEach { date ->
            Text(
                date.format(formatter),
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = WardrobeTheme.extendedColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChipRow(
    label: String,
    values: List<String>,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> WardrobeFilterChip(label = value, selected = false, onClick = {}) }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.displayMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
    }
}

private const val MAX_HISTORY_ROWS = 20
