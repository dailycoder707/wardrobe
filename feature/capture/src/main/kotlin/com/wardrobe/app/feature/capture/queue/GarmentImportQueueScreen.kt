package com.wardrobe.app.feature.capture.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.garment.ImportQueueItem
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentImportQueueScreen(
    onDone: () -> Unit,
    onReviewItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarmentImportQueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Importing ${queue.size} item${if (queue.size == 1) "" else "s"}") },
                actions = { TextButton(onClick = onDone) { Text("Done") } },
            )
        },
    ) { innerPadding ->
        GarmentImportQueueList(
            queue = queue,
            innerPadding = innerPadding,
            onReviewItem = onReviewItem,
            onRetry = viewModel::onRetry,
        )
    }
}

@Composable
private fun GarmentImportQueueList(
    queue: List<ImportQueueItem>,
    innerPadding: PaddingValues,
    onReviewItem: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        items(queue, key = { it.id }) { item ->
            GarmentImportQueueRow(item = item, onReviewItem = onReviewItem, onRetry = onRetry)
        }
    }
}

@Composable
private fun GarmentImportQueueRow(
    item: ImportQueueItem,
    onReviewItem: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    val fileName = item.sourceFilePath.substringAfterLast('/')
    ListItem(
        headlineContent = { Text(fileName) },
        supportingContent = { Text(item.status.displayLabel()) },
        trailingContent = { GarmentImportQueueRowTrailing(item, onRetry) },
        modifier =
            Modifier.fillMaxWidth().clickable(enabled = item.status == ImportQueueItemStatus.READY_FOR_REVIEW) {
                onReviewItem(item.id)
            },
    )
}

@Composable
private fun GarmentImportQueueRowTrailing(
    item: ImportQueueItem,
    onRetry: (Long) -> Unit,
) {
    when (item.status) {
        ImportQueueItemStatus.PENDING,
        ImportQueueItemStatus.IMPORTING,
        ImportQueueItemStatus.EXTRACTING_GARMENT,
        ImportQueueItemStatus.ENHANCING_PRESENTATION,
        ImportQueueItemStatus.RECONSTRUCTING_OCCLUSIONS,
        ImportQueueItemStatus.GENERATING_METADATA,
        -> {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        ImportQueueItemStatus.READY_FOR_REVIEW -> {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Ready to review",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        ImportQueueItemStatus.SAVING -> {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        ImportQueueItemStatus.COMPLETED -> {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary)
        }

        ImportQueueItemStatus.FAILED -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
                IconButton(onClick = { onRetry(item.id) }) {
                    // M22 fix: this used the Close glyph, which reads as "dismiss" rather
                    // than "try again" — Refresh matches every other retry affordance in
                    // the app (e.g. GarmentReviewAiPanels' RetryButton).
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                }
            }
        }
    }
}

private fun ImportQueueItemStatus.displayLabel(): String =
    when (this) {
        ImportQueueItemStatus.PENDING -> "Importing…"
        ImportQueueItemStatus.IMPORTING -> "Importing…"
        ImportQueueItemStatus.EXTRACTING_GARMENT -> "Isolating garment…"
        ImportQueueItemStatus.ENHANCING_PRESENTATION -> "Enhancing presentation…"
        ImportQueueItemStatus.RECONSTRUCTING_OCCLUSIONS -> "Reconstructing hidden areas…"
        ImportQueueItemStatus.GENERATING_METADATA -> "Generating details…"
        ImportQueueItemStatus.READY_FOR_REVIEW -> "Ready — tap to add details"
        ImportQueueItemStatus.SAVING -> "Saving…"
        ImportQueueItemStatus.COMPLETED -> "Saved"
        ImportQueueItemStatus.FAILED -> "Failed — tap retry"
    }
