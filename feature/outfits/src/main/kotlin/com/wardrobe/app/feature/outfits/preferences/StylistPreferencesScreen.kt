package com.wardrobe.app.feature.outfits.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.styling.RecommendationPreferences

private data class PreferenceToggle(
    val label: String,
    val isChecked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylistPreferencesScreen(
    modifier: Modifier = Modifier,
    viewModel: StylistPreferencesViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Stylist Preferences") }) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            items(buildToggles(prefs, viewModel)) { toggle ->
                PreferenceRow(toggle)
                HorizontalDivider()
            }
        }
    }
}

private fun buildToggles(
    prefs: RecommendationPreferences,
    viewModel: StylistPreferencesViewModel,
): List<PreferenceToggle> =
    listOf(
        PreferenceToggle("Include shoes", prefs.includeShoes) { viewModel.update { p -> p.copy(includeShoes = it) } },
        PreferenceToggle("Include bags", prefs.includeBags) { viewModel.update { p -> p.copy(includeBags = it) } },
        PreferenceToggle(
            "Include jewelry",
            prefs.includeJewelry,
        ) { checked -> viewModel.update { p -> p.copy(includeJewelry = checked) } },
        PreferenceToggle("Include watch", prefs.includeWatch) { viewModel.update { p -> p.copy(includeWatch = it) } },
        PreferenceToggle("Include belt", prefs.includeBelt) { viewModel.update { p -> p.copy(includeBelt = it) } },
        PreferenceToggle(
            "Include hair accessories",
            prefs.includeHairAccessories,
        ) { checked -> viewModel.update { p -> p.copy(includeHairAccessories = checked) } },
        PreferenceToggle(
            "Include sunglasses",
            prefs.includeSunglasses,
        ) { checked -> viewModel.update { p -> p.copy(includeSunglasses = checked) } },
        PreferenceToggle(
            "Prefer favorites",
            prefs.preferFavorites,
        ) { checked -> viewModel.update { p -> p.copy(preferFavorites = checked) } },
        PreferenceToggle(
            "Prefer rarely worn items",
            prefs.preferRarelyWorn,
        ) { checked -> viewModel.update { p -> p.copy(preferRarelyWorn = checked) } },
        PreferenceToggle(
            "Avoid recently worn items",
            prefs.avoidRecentlyWorn,
        ) { checked -> viewModel.update { p -> p.copy(avoidRecentlyWorn = checked) } },
        PreferenceToggle(
            "Favorite color bias",
            prefs.favoriteColorBias,
        ) { checked -> viewModel.update { p -> p.copy(favoriteColorBias = checked) } },
        PreferenceToggle(
            "Prefer comfortable fit",
            prefs.preferComfortableFit,
        ) { checked -> viewModel.update { p -> p.copy(preferComfortableFit = checked) } },
    )

@Composable
private fun PreferenceRow(toggle: PreferenceToggle) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(toggle.label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = toggle.isChecked, onCheckedChange = toggle.onToggle)
    }
}
