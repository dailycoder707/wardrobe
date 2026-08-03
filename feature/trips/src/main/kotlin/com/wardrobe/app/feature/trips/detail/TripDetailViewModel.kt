package com.wardrobe.app.feature.trips.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.feature.trips.navigation.TripDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private val DATE_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@HiltViewModel
class TripDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val tripRepository: TripRepository,
    ) : ViewModel() {
        private val tripId = TripId(savedStateHandle.toRoute<TripDetailRoute>().tripId)
        private val isGeneratingFlow = MutableStateFlow(false)

        val uiState: StateFlow<TripDetailUiState> =
            combine(
                tripRepository.observeTrips(),
                tripRepository.observePackingList(tripId),
                isGeneratingFlow,
            ) { trips, packingList, isGenerating ->
                val trip = trips.firstOrNull { it.id == tripId }
                if (trip == null) {
                    TripDetailUiState(isLoading = false, notFound = true)
                } else {
                    TripDetailUiState(
                        isLoading = false,
                        name = trip.name?.takeUnless { it.isBlank() } ?: trip.destination,
                        destination = trip.destination,
                        dateRangeLabel =
                            "${trip.dateRange.start.format(DATE_LABEL_FORMATTER)} " +
                                "– ${trip.dateRange.end.format(DATE_LABEL_FORMATTER)}",
                        luggageSizeLabel =
                            trip.luggageSize
                                ?.name
                                ?.lowercase()
                                ?.replace('_', ' '),
                        packingItemCount = packingList.size,
                        packedCount = packingList.count { it.isPacked },
                        isGeneratingPackingList = isGenerating,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TripDetailUiState(),
            )

        /** Phase 9 Trip Intelligence — generates real per-day outfits,
         * toiletries, and reminders from this trip's own duration, then
         * saves the result as this trip's packing list wholesale (the
         * existing "replace, don't merge" contract `savePackingList`
         * already has). */
        fun generatePackingList() {
            viewModelScope.launch {
                isGeneratingFlow.value = true
                val suggestions = tripRepository.generatePackingSuggestions(tripId)
                tripRepository.savePackingList(tripId, suggestions)
                isGeneratingFlow.update { false }
            }
        }

        fun deleteTrip(onDeleted: () -> Unit) {
            viewModelScope.launch {
                tripRepository.deleteTrip(tripId)
                onDeleted()
            }
        }
    }
