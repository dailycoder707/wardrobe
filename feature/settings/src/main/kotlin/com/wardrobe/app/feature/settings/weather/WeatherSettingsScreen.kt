package com.wardrobe.app.feature.settings.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherPreferences

private data class SettingsToggle(
    val label: String,
    val isChecked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: WeatherSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.update { it.copy(useDeviceLocation = granted) }
        }

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Weather Settings") }) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            items(buildToggles(prefs, viewModel, locationPermissionLauncher::launch)) { toggle ->
                ToggleRow(toggle)
                HorizontalDivider()
            }
            if (!prefs.useDeviceLocation) {
                item { ManualLocationFields(prefs, viewModel) }
            }
            item { TemperatureUnitRow(prefs, viewModel) }
            item { RefreshIntervalRow(prefs, viewModel) }
        }
    }
}

private fun buildToggles(
    prefs: WeatherPreferences,
    viewModel: WeatherSettingsViewModel,
    requestLocationPermission: (String) -> Unit,
): List<SettingsToggle> =
    listOf(
        SettingsToggle("Use weather", prefs.useWeather) { checked ->
            viewModel.update { it.copy(useWeather = checked) }
        },
        SettingsToggle("Offline only (never fetch, use cache)", prefs.offlineOnly) { checked ->
            viewModel.update { it.copy(offlineOnly = checked) }
        },
        SettingsToggle("Use device location", prefs.useDeviceLocation) { checked ->
            if (checked) {
                requestLocationPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                viewModel.update { it.copy(useDeviceLocation = false) }
            }
        },
    )

@Composable
private fun ToggleRow(toggle: SettingsToggle) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(toggle.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = toggle.isChecked, onCheckedChange = toggle.onToggle)
    }
}

@Composable
private fun ManualLocationFields(
    prefs: WeatherPreferences,
    viewModel: WeatherSettingsViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Manual location", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = prefs.manualLocationLabel.orEmpty(),
            onValueChange = { label -> viewModel.update { it.copy(manualLocationLabel = label.ifBlank { null }) } },
            label = { Text("Location label") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = prefs.manualLatitude?.toString().orEmpty(),
            onValueChange = { text -> viewModel.update { it.copy(manualLatitude = text.toDoubleOrNull()) } },
            label = { Text("Latitude") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = prefs.manualLongitude?.toString().orEmpty(),
            onValueChange = { text -> viewModel.update { it.copy(manualLongitude = text.toDoubleOrNull()) } },
            label = { Text("Longitude") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TemperatureUnitRow(
    prefs: WeatherPreferences,
    viewModel: WeatherSettingsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Temperature units", style = MaterialTheme.typography.bodyLarge)
        Row {
            TemperatureUnit.entries.forEach { unit ->
                TextButton(onClick = { viewModel.update { it.copy(temperatureUnit = unit) } }) {
                    Text(
                        if (unit == TemperatureUnit.CELSIUS) "°C" else "°F",
                        style =
                            if (prefs.temperatureUnit == unit) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                    )
                }
            }
        }
    }
}

private const val MIN_REFRESH_INTERVAL_HOURS = 1
private const val MAX_REFRESH_INTERVAL_HOURS = 12

@Composable
private fun RefreshIntervalRow(
    prefs: WeatherPreferences,
    viewModel: WeatherSettingsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Refresh every ${prefs.refreshIntervalHours}h", style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val next = (prefs.refreshIntervalHours - 1).coerceAtLeast(MIN_REFRESH_INTERVAL_HOURS)
                    viewModel.update { it.copy(refreshIntervalHours = next) }
                },
            ) { Text("−") }
            IconButton(
                onClick = {
                    val next = (prefs.refreshIntervalHours + 1).coerceAtMost(MAX_REFRESH_INTERVAL_HOURS)
                    viewModel.update { it.copy(refreshIntervalHours = next) }
                },
            ) { Text("+") }
        }
    }
}
