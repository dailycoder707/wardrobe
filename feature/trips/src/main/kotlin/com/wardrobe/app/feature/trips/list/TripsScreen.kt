package com.wardrobe.app.feature.trips.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.ui.components.EmptyState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onOpenTrip: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Trips") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Plan a trip")
            }
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.FlightTakeoff,
                headline = "No trips planned",
                supportingText = "Plan a trip to get complete packing suggestions from your own wardrobe.",
                actionLabel = "Plan a Trip",
                onAction = { showCreateDialog = true },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.trips, key = { it.id }) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }) }
            }
        }
    }

    if (showCreateDialog) {
        CreateTripDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, destination, start, end, luggage ->
                viewModel.createTrip(name, destination, start, end, luggage)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun TripCard(
    trip: TripUiModel,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(trip.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${trip.destination} · ${trip.dateRangeLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}

private val DATE_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
private fun CreateTripDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, LocalDate, LocalDate, LuggageSize?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDateText by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }

    fun parsedDates(): Pair<LocalDate, LocalDate>? =
        runCatching {
            LocalDate.parse(startDateText, DATE_INPUT_FORMATTER) to LocalDate.parse(endDateText, DATE_INPUT_FORMATTER)
        }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan a Trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Trip name (optional)") })
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                )
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text("Start date (YYYY-MM-DD)") },
                )
                OutlinedTextField(
                    value = endDateText,
                    onValueChange = { endDateText = it },
                    label = { Text("End date (YYYY-MM-DD)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dates = parsedDates() ?: return@TextButton
                    if (destination.isBlank()) return@TextButton
                    onCreate(name, destination, dates.first, dates.second, null)
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
