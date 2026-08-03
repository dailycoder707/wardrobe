package com.wardrobe.app.feature.outfits.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.model.garment.GarmentFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/**
 * Phase 9 — Duplicate Detection. Purely surfaces
 * [WardrobeIntelligenceRepository.observeDuplicateGroups] for review; never
 * offers a delete action here (the brief: "do not delete automatically" —
 * a user who wants to act on a duplicate does so from Garment Detail/Closet,
 * same as any other item).
 */
@HiltViewModel
class DuplicatesViewModel
    @Inject
    constructor(
        wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
        garmentRepository: GarmentRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DuplicatesUiState> =
            combine(
                wardrobeIntelligenceRepository.observeDuplicateGroups(),
                garmentRepository.observeGarments(GarmentFilter(status = null)),
            ) { groups, garments ->
                val garmentsById = garments.associateBy { it.id }
                DuplicatesUiState(
                    isLoading = false,
                    groups =
                        groups.map { group ->
                            DuplicateGroupUiModel(
                                garmentNames = group.garmentIds.map { garmentsById[it]?.name ?: "Untitled item" },
                                matchedOnBrand = group.matchedOnBrand,
                                similarUsage = group.similarUsage,
                            )
                        },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DuplicatesUiState(isLoading = true),
            )
    }
