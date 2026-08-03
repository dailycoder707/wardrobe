package com.wardrobe.app.feature.closet.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Debug-only diagnostics screen. Never registered in a release build's nav
 * graph (see `WardrobeNavHost` in `:app`) — this is the actual enforcement
 * that keeps it out of release builds, not just a hidden entry point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperPanelScreen(
    onBack: () -> Unit,
    currentDestination: String,
    modifier: Modifier = Modifier,
    viewModel: DeveloperPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Developer Panel") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        DeveloperPanelContent(state, currentDestination, Modifier.padding(innerPadding))
    }
}

@Composable
private fun DeveloperPanelContent(
    state: DeveloperPanelUiState,
    currentDestination: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DeveloperPanelStateSections(state, currentDestination)
        DeveloperPanelDiagnosticsSections(state)
    }
}

@Composable
private fun DeveloperPanelStateSections(
    state: DeveloperPanelUiState,
    currentDestination: String,
) {
    Section("Navigation") {
        Row("Current destination", currentDestination)
    }

    Section("Database Counts") {
        Row("Garments", state.counts.garments.toString())
        Row("Categories", state.counts.categories.toString())
        Row("Colors", state.counts.colors.toString())
        Row("Brands", state.counts.brands.toString())
        Row("Materials", state.counts.materials.toString())
        Row("Tags", state.counts.tags.toString())
    }

    Section("Closet Screen State") {
        Row("Search query", state.diagnostics.searchQuery.ifBlank { "(none)" })
        Row("Active filters", "${state.diagnostics.activeFilterCount} — ${state.diagnostics.filterSummary}")
        Row("Sort", state.diagnostics.sortSummary)
        Row("Result count", state.diagnostics.resultCount.toString())
    }

    Section("Image Cache") {
        Row("Files on disk", state.imageFileCount.toString())
        Row("Total size", "${state.imageCacheBytes / (1024 * 1024)} MB")
    }

    Section("Memory") {
        Row("Used", "${state.memoryUsage.usedMb} MB")
        Row("Max heap", "${state.memoryUsage.maxMb} MB")
    }
}

@Composable
private fun DeveloperPanelDiagnosticsSections(state: DeveloperPanelUiState) {
    Section("Compose Recomposition Counters") {
        if (state.recompositionCounts.isEmpty()) {
            Text("No tracked composables have recomposed yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.recompositionCounts.forEach { (label, count) -> Row(label, count.toString()) }
        }
    }

    Section("Recent Processing Jobs") {
        if (state.recentJobs.isEmpty()) {
            Text("No jobs recorded.", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.recentJobs.forEach { job -> Row(job.name, job.state) }
        }
    }

    Section("Outfits & Calendar") {
        Row("Saved outfits", state.savedOutfitCount.toString())
        Row("Calendar assignments (planned)", state.plannedWearEventCount.toString())
        if (state.recentOutfitSaves.isEmpty()) {
            Text("No outfits saved yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.recentOutfitSaves.forEach { save -> Row(save.name, formatEpochMilli(save.createdAtEpochMilli)) }
        }
    }

    Section("Outfit Builder") {
        Row("Open", if (state.builderIsOpen) "Yes" else "No")
        Row("Slots filled", "${state.builderFilledSlotCount} / ${state.builderTotalSlotCount}")
        Row("Undo stack size", state.undoStackSize.toString())
        Row("Redo stack size", state.redoStackSize.toString())
    }

    Section("Wardrobe Intelligence") {
        Row("Cache hits", state.stats.cacheHits.toString())
        Row("Cache misses", state.stats.cacheMisses.toString())
        Row("Active flow subscriptions", state.stats.activeFlowSubscriptions.toString())
        if (state.stats.queryTimingsMillis.isEmpty()) {
            Text("No derived-computation timings recorded yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.stats.queryTimingsMillis.forEach { (label, millis) -> Row(label, "${millis}ms") }
        }
    }

    Section("Personal Wardrobe Stylist") {
        Row("Last generation time", "${state.recommendations.lastGenerationTimeMillis}ms")
        Row("Suggestions generated", state.recommendations.lastSuggestionCount.toString())
        Row("Top score", state.recommendations.lastTopScore.toString())
        Row("Active style rules", state.recommendations.activeRuleCount.toString())
        Row("Active flow subscriptions", state.recommendations.activeFlowSubscriptions.toString())
        Row("Weather source", state.recommendations.weatherSource.name)
        Row("Weather cache age", state.recommendations.weatherCacheAgeMinutes?.let { "${it}min" } ?: "n/a")
        Row("Rules applied", state.recommendations.rulesAppliedCount.toString())
        Row("Planned outfit used", state.recommendations.plannedOutfitUsed.toString())
        Row(
            "Context notes",
            state.recommendations.contextNotes.size
                .toString(),
        )
    }

    DeveloperPanelSyncDiagnosticsSection(state)
}

@Composable
private fun DeveloperPanelSyncDiagnosticsSection(state: DeveloperPanelUiState) {
    Section("Sync Diagnostics") {
        Row("Connected device", state.sync.connectedDeviceName ?: "Not paired")
        Row("Pending uploads", state.sync.pendingChangeCount.toString())
        Row("Bytes transferred (last session)", "${state.sync.bytesTransferredLastSession} B")
        Row("Last successful sync", state.sync.lastSuccessLabel)
        Row("Last failed sync", state.sync.lastFailureLabel)
        Row("Conflicts resolved", state.sync.conflictsResolvedCount.toString())
        Row("Queue size", state.sync.queueSize.toString())
        Row(
            "Sync worker status",
            state.recentJobs.firstOrNull { it.name == "SyncWorker" }?.state ?: "Not scheduled",
        )
    }
}

private fun formatEpochMilli(epochMilli: Long): String =
    Instant
        .ofEpochMilli(epochMilli)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun Row(
    label: String,
    value: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = WardrobeTheme.extendedColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
