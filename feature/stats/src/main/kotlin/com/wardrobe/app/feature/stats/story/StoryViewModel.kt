package com.wardrobe.app.feature.stats.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.ui.debug.StatsDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private val STORY_WINDOW = StatsWindow.ONE_YEAR

internal data class StoryReferenceData(
    val garments: List<Garment>,
    val outfits: List<Outfit>,
    val colorsById: Map<ColorId, Color>,
)

internal data class StoryStatsData(
    val usage: UsageStats,
    val dormant: List<DormantItem>,
    val costPerWear: List<CostPerWearEntry>,
    val outfitsWornCount: Int,
    val weekendDressCode: DressCode?,
)

@HiltViewModel
class StoryViewModel
    @Inject
    constructor(
        statsRepository: StatsRepository,
        garmentRepository: GarmentRepository,
        outfitRepository: OutfitRepository,
        colorRepository: ColorRepository,
        clock: Clock,
    ) : ViewModel() {
        private val today = LocalDate.now(clock)

        private val referenceFlow =
            combine(
                garmentRepository.observeGarments(GarmentFilter()),
                outfitRepository.observeOutfits(OutfitFilter()),
                colorRepository.observeAll(),
            ) { garments, outfits, colors ->
                StoryReferenceData(garments, outfits, colors.associateBy { it.id })
            }

        private val statsFlow =
            combine(
                statsRepository.observeUsageStats(STORY_WINDOW),
                statsRepository.observeDormantItems(StatsWindow.ALL_TIME),
                statsRepository.observeCostPerWear(),
                statsRepository.observeOutfitWearEventCount(STORY_WINDOW),
                statsRepository.observeTopDressCode(STORY_WINDOW, isWeekend = true),
            ) { usage, dormant, costPerWear, outfitsWornCount, weekendDressCode ->
                StoryStatsData(usage, dormant, costPerWear, outfitsWornCount, weekendDressCode)
            }

        private val weekdayDressCodeFlow = statsRepository.observeTopDressCode(STORY_WINDOW, isWeekend = false)

        val uiState: StateFlow<StoryUiState> =
            combine(referenceFlow, statsFlow, weekdayDressCodeFlow) { ref, stats, weekdayDressCode ->
                StoryUiState(isLoading = false, cards = buildStoryCards(ref, stats, weekdayDressCode, today))
            }.onStart { StatsDiagnostics.onSubscribed() }
                .onCompletion { StatsDiagnostics.onUnsubscribed() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = StoryUiState(),
                )
    }
