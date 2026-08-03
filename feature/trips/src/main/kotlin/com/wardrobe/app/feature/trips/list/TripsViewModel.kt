package com.wardrobe.app.feature.trips.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.model.trip.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private val DATE_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@HiltViewModel
class TripsViewModel
    @Inject
    constructor(
        private val tripRepository: TripRepository,
    ) : ViewModel() {
        val uiState: StateFlow<TripsUiState> =
            tripRepository
                .observeTrips()
                .map { trips ->
                    TripsUiState(
                        isLoading = false,
                        trips =
                            trips
                                .sortedBy { it.dateRange.start }
                                .map { trip ->
                                    TripUiModel(
                                        id = trip.id.value,
                                        name = trip.name?.takeUnless { it.isBlank() } ?: trip.destination,
                                        destination = trip.destination,
                                        dateRangeLabel =
                                            "${trip.dateRange.start.format(DATE_LABEL_FORMATTER)} " +
                                                "– ${trip.dateRange.end.format(DATE_LABEL_FORMATTER)}",
                                    )
                                },
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = TripsUiState(),
                )

        fun createTrip(
            name: String,
            destination: String,
            startDate: LocalDate,
            endDate: LocalDate,
            luggageSize: LuggageSize?,
        ) {
            viewModelScope.launch {
                tripRepository.saveTrip(
                    Trip(
                        id = TripId(0),
                        name = name.takeUnless { it.isBlank() },
                        destination = destination,
                        dateRange = DateRange(startDate, endDate),
                        activities = emptyList(),
                        luggageSize = luggageSize,
                        createdAt = Instant.now(),
                    ),
                )
            }
        }

        fun deleteTrip(id: Long) {
            viewModelScope.launch { tripRepository.deleteTrip(TripId(id)) }
        }
    }
