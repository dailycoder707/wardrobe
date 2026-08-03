package com.wardrobe.app.feature.trips.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onOpenPacking: (Long) -> Unit,
    tripId: Long,
    modifier: Modifier = Modifier,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteTrip(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete trip")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.destination, style = MaterialTheme.typography.titleLarge)
            Text(
                state.dateRangeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            state.luggageSizeLabel?.let {
                Text(
                    "Luggage: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }

            if (state.packingItemCount == 0) {
                Text(
                    "No packing list yet — generate a real one from your own wardrobe.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = viewModel::generatePackingList) {
                    if (state.isGeneratingPackingList) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Generate Packing List")
                }
            } else {
                Text(
                    "${state.packedCount} of ${state.packingItemCount} packed",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { onOpenPacking(tripId) }) { Text("View Packing List") }
                OutlinedButton(onClick = viewModel::generatePackingList) { Text("Regenerate") }
            }
        }
    }
}
