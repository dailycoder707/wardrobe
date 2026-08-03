package com.wardrobe.app.feature.outfits.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.intelligence.CapsuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 9 — Capsule Suggestions. Each [CapsuleType] is generated on demand
 * (a suspend call, the same "not continuously recomputed" posture
 * `RecommendationsViewModel` already uses), never a full daily outfit — just
 * the curated small item set `WardrobeIntelligenceRepository.suggestCapsule`
 * returns.
 */
@HiltViewModel
class CapsulesViewModel
    @Inject
    constructor(
        private val wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
        private val garmentRepository: GarmentRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(CapsulesUiState())
        val uiState: StateFlow<CapsulesUiState> = mutableUiState.asStateFlow()

        init {
            select(CapsuleType.OFFICE)
        }

        fun select(type: CapsuleType) {
            mutableUiState.update { it.copy(isLoading = true, selectedType = type) }
            viewModelScope.launch {
                val capsule = wardrobeIntelligenceRepository.suggestCapsule(type)
                val garmentsById =
                    garmentRepository.observeGarments(GarmentFilter(status = null)).first().associateBy { it.id }
                val items =
                    capsule.itemsBySlot.map { (slot, garmentIds) ->
                        CapsuleItemUiModel(
                            slotLabel = slot.name.lowercase().replaceFirstChar(Char::uppercase),
                            garmentNames = garmentIds.map { garmentsById[it]?.name ?: "Untitled item" },
                        )
                    }
                mutableUiState.update {
                    it.copy(isLoading = false, explanation = capsule.explanation, items = items)
                }
            }
        }
    }
