package com.wardrobe.app.feature.trips.packing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.feature.trips.navigation.PackingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val UNCATEGORIZED_LABEL = "Other"

@HiltViewModel
class PackingViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val tripRepository: TripRepository,
        garmentRepository: GarmentRepository,
    ) : ViewModel() {
        private val tripId = TripId(savedStateHandle.toRoute<PackingRoute>().tripId)

        val uiState: StateFlow<PackingUiState> =
            combine(
                tripRepository.observePackingList(tripId),
                garmentRepository.observeGarments(GarmentFilter(status = null)),
            ) { items, garments ->
                val garmentsById = garments.associateBy { it.id }
                val groups =
                    items
                        .groupBy { it.category ?: UNCATEGORIZED_LABEL }
                        .map { (category, groupItems) ->
                            PackingGroupUiModel(
                                category = category,
                                items =
                                    groupItems.map { item ->
                                        val name =
                                            item.garmentId?.let { garmentsById[it]?.name ?: "Untitled item" }
                                                ?: item.freeTextName.orEmpty()
                                        PackingItemUiModel(
                                            id = item.id.value,
                                            garmentId = item.garmentId?.value,
                                            name = name,
                                            isPacked = item.isPacked,
                                            rationale = item.rationale,
                                        )
                                    },
                            )
                        }
                PackingUiState(isLoading = false, groups = groups)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PackingUiState(),
            )

        fun togglePacked(
            itemId: Long,
            isPacked: Boolean,
        ) {
            viewModelScope.launch { tripRepository.setPacked(PackingListItemId(itemId), isPacked) }
        }
    }
