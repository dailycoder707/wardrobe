package com.wardrobe.app.feature.outfits.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CapsulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Capsule Suggestions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CapsuleType.entries) { type ->
                    WardrobeFilterChip(
                        label =
                            type.name
                                .lowercase()
                                .replace('_', ' ')
                                .replaceFirstChar(Char::uppercase),
                        selected = type == state.selectedType,
                        onClick = { viewModel.select(type) },
                    )
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else {
                CapsuleContent(state)
            }
        }
    }
}

@Composable
private fun CapsuleContent(state: CapsulesUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(state.explanation, style = MaterialTheme.typography.bodyLarge) }
        if (state.isEmpty) {
            item {
                Text(
                    "Not enough matching items in your closet yet for this capsule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }
        }
        items(state.items) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.slotLabel, style = MaterialTheme.typography.titleMedium)
                item.garmentNames.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
