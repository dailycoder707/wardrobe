package com.wardrobe.app.feature.stats.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
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
private val HEALTH_WINDOW = StatsWindow.ONE_YEAR

internal data class HealthReferenceData(
    val garments: List<Garment>,
    val categories: List<Category>,
    val brandsById: Map<BrandId, Brand>,
    val colorsById: Map<ColorId, Color>,
)

internal data class HealthStatsData(
    val usage: UsageStats,
    val costPerWear: List<CostPerWearEntry>,
    val dormant: List<DormantItem>,
)

@HiltViewModel
class HealthViewModel
    @Inject
    constructor(
        statsRepository: StatsRepository,
        garmentRepository: GarmentRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        colorRepository: ColorRepository,
        clock: Clock,
    ) : ViewModel() {
        private val today = LocalDate.now(clock)

        private val referenceFlow =
            combine(
                garmentRepository.observeGarments(GarmentFilter()),
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                colorRepository.observeAll(),
            ) { garments, categories, brands, colors ->
                HealthReferenceData(garments, categories, brands.associateBy { it.id }, colors.associateBy { it.id })
            }

        private val statsFlow =
            combine(
                statsRepository.observeUsageStats(HEALTH_WINDOW),
                statsRepository.observeCostPerWear(),
                statsRepository.observeDormantItems(StatsWindow.ALL_TIME),
            ) { usage, costPerWear, dormant -> HealthStatsData(usage, costPerWear, dormant) }

        val uiState: StateFlow<HealthUiState> =
            combine(referenceFlow, statsFlow) { ref, stats ->
                HealthUiState(isLoading = false, advisories = buildHealthAdvisories(ref, stats, today))
            }.onStart { StatsDiagnostics.onSubscribed() }
                .onCompletion { StatsDiagnostics.onUnsubscribed() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = HealthUiState(),
                )
    }
