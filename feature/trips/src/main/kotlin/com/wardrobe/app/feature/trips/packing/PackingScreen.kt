package com.wardrobe.app.feature.trips.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingScreen(
    onBack: () -> Unit,
    onTryOnGarment: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Packing List") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.ArrowBack,
                headline = "Nothing to pack yet",
                supportingText = "Generate a packing list from the trip's own detail screen.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.groups.forEach { group ->
                    item { Text(group.category, style = MaterialTheme.typography.titleMedium) }
                    items(group.items, key = { it.id }) { item ->
                        PackingItemRow(
                            item,
                            onToggle = { checked -> viewModel.togglePacked(item.id, checked) },
                            onTryOn = item.garmentId?.let { garmentId -> { onTryOnGarment(garmentId) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PackingItemRow(
    item: PackingItemUiModel,
    onToggle: (Boolean) -> Unit,
    onTryOn: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isPacked, onCheckedChange = onToggle)
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            item.rationale?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }
        }
        if (onTryOn != null) {
            IconButton(onClick = onTryOn) {
                Icon(Icons.Filled.Checkroom, contentDescription = "Try on ${item.name}")
            }
        }
    }
}
