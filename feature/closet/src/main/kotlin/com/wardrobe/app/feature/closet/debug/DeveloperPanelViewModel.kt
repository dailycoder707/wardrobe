package com.wardrobe.app.feature.closet.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.sync.SyncOutcome
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnostics
import com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnosticsSnapshot
import com.wardrobe.app.core.ui.debug.RecommendationDiagnostics
import com.wardrobe.app.core.ui.debug.RecommendationDiagnosticsSnapshot
import com.wardrobe.app.core.ui.debug.RecompositionTracker
import com.wardrobe.app.core.ui.debug.StatsDiagnostics
import com.wardrobe.app.core.ui.debug.StatsDiagnosticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val BYTES_PER_MB = 1024L * 1024L
private const val MAX_RECENT_OUTFIT_SAVES = 5
private const val EPOCH_YEAR = 1970

private const val SYNC_HISTORY_LIMIT = 10

private val WORK_TAGS =
    listOf(
        "ImageProcessingWorker",
        "OrphanedImageCleanupWorker",
        "BackupExportWorker",
        "BackupRestoreWorker",
        "WeatherRefreshWorker",
        "SyncWorker",
    )

private data class OutfitDiagnosticsData(
    val savedOutfitCount: Int,
    val plannedWearEventCount: Int,
    val recentOutfitSaves: List<RecentOutfitSave>,
)

private data class DiagnosticsBundle(
    val closet: ClosetDiagnosticsSnapshot,
    val builder: com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnosticsSnapshot,
    val stats: com.wardrobe.app.core.ui.debug.StatsDiagnosticsSnapshot,
    val recommendations: com.wardrobe.app.core.ui.debug.RecommendationDiagnosticsSnapshot,
)

/** Aggregates every repository the Developer Panel reads from so
 * [DeveloperPanelViewModel]'s own constructor stays under detekt's
 * LongParameterList threshold — Hilt injects this the same as any other
 * dependency, it just groups several instead of holding them individually. */
class DeveloperPanelRepositories
    @Inject
    constructor(
        val garmentRepository: GarmentRepository,
        val categoryRepository: CategoryRepository,
        val colorRepository: ColorRepository,
        val brandRepository: BrandRepository,
        val materialRepository: MaterialRepository,
        val tagRepository: TagRepository,
        val outfitRepository: OutfitRepository,
        val wearEventRepository: WearEventRepository,
        val syncRepository: SyncRepository,
    )

private data class SyncDiagnosticsData(
    val connectedDeviceName: String?,
    val pendingChangeCount: Int,
    val lastSuccessLabel: String,
    val lastFailureLabel: String,
    val conflictsResolvedCount: Int,
    val queueSize: Int,
    val bytesTransferredLastSession: Long,
)

@HiltViewModel
class DeveloperPanelViewModel
    @Inject
    constructor(
        repositories: DeveloperPanelRepositories,
        private val imageFileStore: ImageFileStore,
        private val closetDiagnostics: ClosetDiagnostics,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val workManager = WorkManager.getInstance(context)

        private val countsFlow =
            combine(
                repositories.garmentRepository.observeGarments(GarmentFilter(status = null)).map { it.size },
                repositories.categoryRepository.observeAll().map { it.size },
                repositories.colorRepository.observeAll().map { it.size },
                repositories.brandRepository.observeAll().map { it.size },
                repositories.materialRepository.observeAll().map { it.size },
                repositories.tagRepository.observeAll().map { it.size },
            ) { counts: Array<Int> ->
                DatabaseCounts(
                    garments = counts[0],
                    categories = counts[1],
                    colors = counts[2],
                    brands = counts[3],
                    materials = counts[4],
                    tags = counts[5],
                )
            }

        private val jobsFlow =
            workManager
                .getWorkInfosFlow(WorkQuery.Builder.fromTags(WORK_TAGS).build())
                .map { infos ->
                    infos.map { info ->
                        RecentJob(info.tags.firstOrNull { it in WORK_TAGS } ?: "Job", info.state.name)
                    }
                }

        private val outfitDiagnosticsFlow =
            combine(
                repositories.outfitRepository.observeOutfits(OutfitFilter(isSaved = true, isArchived = null)),
                repositories.wearEventRepository.observeEvents(
                    DateRange(LocalDate.of(EPOCH_YEAR, 1, 1), LocalDate.now().plusYears(1)),
                ),
            ) { outfits, wearEvents ->
                OutfitDiagnosticsData(
                    savedOutfitCount = outfits.size,
                    plannedWearEventCount = wearEvents.count { it.status == WearEventStatus.PLANNED },
                    recentOutfitSaves =
                        outfits
                            .sortedByDescending { it.createdAt }
                            .take(MAX_RECENT_OUTFIT_SAVES)
                            .map { RecentOutfitSave(it.name ?: "Untitled look", it.createdAt.toEpochMilli()) },
                )
            }

        private val diagnosticsFlow =
            combine(
                closetDiagnostics.state,
                OutfitBuilderDiagnostics.state,
                StatsDiagnostics.state,
                RecommendationDiagnostics.state,
            ) { closet, builder, stats, recommendations -> DiagnosticsBundle(closet, builder, stats, recommendations) }

        private val syncDiagnosticsFlow =
            combine(
                repositories.syncRepository.observeStatus(),
                repositories.syncRepository.observeHistory(SYNC_HISTORY_LIMIT),
                repositories.syncRepository.observeUnresolvedConflicts(),
            ) { status, history, unresolvedConflicts ->
                val lastSuccess = history.firstOrNull { it.outcome == SyncOutcome.SUCCESS }
                val lastFailure = history.firstOrNull { it.outcome == SyncOutcome.FAILED }
                val totalDetected = history.sumOf { it.conflictsDetected }
                SyncDiagnosticsData(
                    connectedDeviceName = status.connectedDevice?.displayName,
                    pendingChangeCount = status.pendingChangeCount,
                    lastSuccessLabel = lastSuccess?.startedAt?.toString() ?: "Never",
                    lastFailureLabel = lastFailure?.startedAt?.toString() ?: "None",
                    conflictsResolvedCount = (totalDetected - unresolvedConflicts.size).coerceAtLeast(0),
                    queueSize = history.count { it.finishedAt == null },
                    bytesTransferredLastSession =
                        (
                            history.firstOrNull()?.let {
                                it.bytesSent + it.bytesReceived
                            }
                        ) ?: 0L,
                )
            }

        val uiState: StateFlow<DeveloperPanelUiState> =
            combine(
                countsFlow,
                diagnosticsFlow,
                jobsFlow,
                outfitDiagnosticsFlow,
                syncDiagnosticsFlow,
            ) { counts, diagnosticsBundle, jobs, outfitDiagnostics, syncDiagnostics ->
                val diagnostics = diagnosticsBundle.closet
                val builder = diagnosticsBundle.builder
                val stats = diagnosticsBundle.stats
                val recommendations = diagnosticsBundle.recommendations
                val imageFiles = imageFileStore.allImageFilesOnDisk()
                val runtime = Runtime.getRuntime()
                DeveloperPanelUiState(
                    counts = counts,
                    diagnostics = diagnostics,
                    imageCacheBytes = imageFiles.sumOf { it.length() },
                    imageFileCount = imageFiles.size,
                    memoryUsage =
                        MemoryUsage(
                            usedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
                            maxMb = runtime.maxMemory() / BYTES_PER_MB,
                        ),
                    recompositionCounts = RecompositionTracker.snapshot(),
                    recentJobs = jobs,
                    savedOutfitCount = outfitDiagnostics.savedOutfitCount,
                    plannedWearEventCount = outfitDiagnostics.plannedWearEventCount,
                    recentOutfitSaves = outfitDiagnostics.recentOutfitSaves,
                    builderIsOpen = builder.isOpen,
                    builderFilledSlotCount = builder.filledSlotCount,
                    builderTotalSlotCount = builder.totalSlotCount,
                    undoStackSize = builder.undoStackSize,
                    redoStackSize = builder.redoStackSize,
                    stats =
                        StatsDiagnosticsUiModel(
                            queryTimingsMillis = stats.queryTimingsMillis,
                            cacheHits = stats.cacheHits,
                            cacheMisses = stats.cacheMisses,
                            activeFlowSubscriptions = stats.activeFlowSubscriptions,
                        ),
                    recommendations =
                        RecommendationDiagnosticsUiModel(
                            lastGenerationTimeMillis = recommendations.lastGenerationTimeMillis,
                            lastSuggestionCount = recommendations.lastSuggestionCount,
                            lastTopScore = recommendations.lastTopScore,
                            activeRuleCount = recommendations.activeRuleCount,
                            activeFlowSubscriptions = recommendations.activeFlowSubscriptions,
                            weatherSource = recommendations.weatherSource,
                            weatherCacheAgeMinutes = recommendations.weatherCacheAgeMinutes,
                            rulesAppliedCount = recommendations.rulesAppliedCount,
                            plannedOutfitUsed = recommendations.plannedOutfitUsed,
                            contextNotes = recommendations.contextNotes,
                        ),
                    sync =
                        SyncDiagnosticsUiModel(
                            connectedDeviceName = syncDiagnostics.connectedDeviceName,
                            pendingChangeCount = syncDiagnostics.pendingChangeCount,
                            lastSuccessLabel = syncDiagnostics.lastSuccessLabel,
                            lastFailureLabel = syncDiagnostics.lastFailureLabel,
                            conflictsResolvedCount = syncDiagnostics.conflictsResolvedCount,
                            queueSize = syncDiagnostics.queueSize,
                            bytesTransferredLastSession = syncDiagnostics.bytesTransferredLastSession,
                        ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DeveloperPanelUiState(),
            )
    }
