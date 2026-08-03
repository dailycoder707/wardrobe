package com.wardrobe.app.feature.closet.debug

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.styling.WeatherSource

@Immutable
data class DatabaseCounts(
    val garments: Int = 0,
    val categories: Int = 0,
    val colors: Int = 0,
    val brands: Int = 0,
    val materials: Int = 0,
    val tags: Int = 0,
)

@Immutable
data class MemoryUsage(
    val usedMb: Long,
    val maxMb: Long,
)

@Immutable
data class RecentJob(
    val name: String,
    val state: String,
)

@Immutable
data class RecentOutfitSave(
    val name: String,
    val createdAtEpochMilli: Long,
)

/** Phase 5e addition — read from `core:ui`'s
 * [com.wardrobe.app.core.ui.debug.StatsDiagnostics], reported by
 * `feature:stats`' own ViewModels (query timings are Kotlin-side derived-
 * computation time, e.g. bucketing the Usage Heatmap into monthly/weekly
 * views — not raw SQL execution time, which Room doesn't expose per query). */
@Immutable
data class StatsDiagnosticsUiModel(
    val queryTimingsMillis: Map<String, Long> = emptyMap(),
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val activeFlowSubscriptions: Int = 0,
)

/** Phase 6 addition — read from `core:ui`'s
 * [com.wardrobe.app.core.ui.debug.RecommendationDiagnostics], reported by
 * `feature:outfits`' `RecommendationsViewModel` around its own call into the
 * styling engine (`core:data`, which has no `core:ui` dependency to report
 * into directly — same layering rule [StatsDiagnosticsUiModel] follows). */
@Immutable
data class RecommendationDiagnosticsUiModel(
    val lastGenerationTimeMillis: Long = 0,
    val lastSuggestionCount: Int = 0,
    val lastTopScore: Double = 0.0,
    val activeRuleCount: Int = 0,
    val activeFlowSubscriptions: Int = 0,
    val weatherSource: WeatherSource = WeatherSource.NONE,
    val weatherCacheAgeMinutes: Long? = null,
    val rulesAppliedCount: Int = 0,
    val plannedOutfitUsed: Boolean = false,
    val contextNotes: List<String> = emptyList(),
)

/** Phase 8 addition — read directly from `core:domain`'s `SyncRepository`
 * (no `core:ui` diagnostics bridge needed, unlike stats/recommendations
 * above: sync state is already persisted via `SyncHistoryEntity`/
 * `PairedDeviceEntity`, so there's nothing ephemeral to relay). */
@Immutable
data class SyncDiagnosticsUiModel(
    val connectedDeviceName: String? = null,
    val pendingChangeCount: Int = 0,
    val lastSuccessLabel: String = "Never",
    val lastFailureLabel: String = "None",
    val conflictsResolvedCount: Int = 0,
    val queueSize: Int = 0,
    val bytesTransferredLastSession: Long = 0,
)

/** Phase 5d additions — [savedOutfitCount]/[plannedWearEventCount] are live
 * repository counts (no bridge needed); [builder] is read from
 * `core:ui`'s [com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnostics], the
 * one piece of state that's genuinely ephemeral, in-memory ViewModel state
 * living in a different module (`feature:outfits`). */
@Immutable
data class DeveloperPanelUiState(
    val counts: DatabaseCounts = DatabaseCounts(),
    val diagnostics: ClosetDiagnosticsSnapshot = ClosetDiagnosticsSnapshot(),
    val imageCacheBytes: Long = 0L,
    val imageFileCount: Int = 0,
    val memoryUsage: MemoryUsage = MemoryUsage(0, 0),
    val recompositionCounts: Map<String, Int> = emptyMap(),
    val recentJobs: List<RecentJob> = emptyList(),
    val savedOutfitCount: Int = 0,
    val plannedWearEventCount: Int = 0,
    val recentOutfitSaves: List<RecentOutfitSave> = emptyList(),
    val builderIsOpen: Boolean = false,
    val builderFilledSlotCount: Int = 0,
    val builderTotalSlotCount: Int = 0,
    val undoStackSize: Int = 0,
    val redoStackSize: Int = 0,
    val stats: StatsDiagnosticsUiModel = StatsDiagnosticsUiModel(),
    val recommendations: RecommendationDiagnosticsUiModel = RecommendationDiagnosticsUiModel(),
    val sync: SyncDiagnosticsUiModel = SyncDiagnosticsUiModel(),
)
