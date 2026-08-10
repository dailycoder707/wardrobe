package com.wardrobe.app.feature.capture.review

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.FabricComposition
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.MaterialComposition
import com.wardrobe.app.core.model.garment.QualityVerdict
import com.wardrobe.app.core.model.garment.StagedImage
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.feature.capture.navigation.GarmentReviewMetadataRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val AUTO_SAVE_COUNTDOWN_START_SECONDS = 3
private const val AUTO_SAVE_COUNTDOWN_TICK_MS = 1000L
private const val METADATA_DIAGNOSTICS_TAG = "MetadataPipeline"

/** M23 — same debug-build gate as `core:ai`/`core:data`'s `AiNetworkModule`/
 * `NetworkModule` `isDebugBuild` helpers (M22): checking the manifest's
 * `FLAG_DEBUGGABLE` rather than adding a new `BuildConfig` to a module that
 * doesn't already have one. */
private fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** M23 diagnostics — real-device debugging support: a debug-build-only
 * logcat dump answering "what did the model actually return for this
 * photo?" (requested capabilities, returned values, confidence, resolution
 * outcome) without exposing the image itself or any secret. Top-level
 * (rather than a class method) purely to stay under this project's
 * `TooManyFunctions` threshold — see [formatMetadataPipelineDiagnostics]'s
 * own KDoc for the pure logic this wraps with the debug-build check. */
private fun logMetadataPipelineDiagnostics(
    context: Context,
    staged: StagedImage,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
    categories: List<Category>,
) {
    if (!isDebugBuild(context)) return
    val diagnostics =
        formatMetadataPipelineDiagnostics(
            staged.aiProcessingSummary?.source,
            staged.metadataSuggestions,
            form,
            reference,
            categories,
        )
    Log.d(METADATA_DIAGNOSTICS_TAG, diagnostics)
}

private data class LoadState(
    val isLoading: Boolean = true,
    val needsRestage: Boolean = false,
    val stagingId: String? = null,
    val staged: StagedImage? = null,
    val checksumDuplicateGarmentName: String? = null,
)

private data class SavingState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val didSave: Boolean = false,
    val potentialDuplicates: List<Garment> = emptyList(),
)

private data class TaxonomyCore(
    val categories: List<Category>,
    val brands: List<Brand>,
    val colors: List<Color>,
    val materials: List<Material>,
    val tags: List<Tag>,
)

private data class ReferenceData(
    val categories: List<Category>,
    val brands: List<Brand>,
    val colors: List<Color>,
    val materials: List<Material>,
    val tags: List<Tag>,
    val fabrics: List<Fabric>,
    val occasions: List<Occasion>,
)

private data class CoreUiPieces(
    val load: LoadState,
    val form: GarmentMetadataFormState,
    val saving: SavingState,
    val reference: ReferenceData,
    val retryingStage: ImageRetryStage?,
)

/** Bundles the seven reference-data repositories purely to keep
 * [GarmentReviewMetadataViewModel]'s own constructor under this project's
 * `LongParameterList` threshold — the same "bag of repositories" pattern
 * `core:data`'s `SyncEntityRegistry.TaxonomyDaos` already uses. */
class ReviewTaxonomyRepositories
    @Inject
    constructor(
        val categoryRepository: CategoryRepository,
        val brandRepository: BrandRepository,
        val colorRepository: ColorRepository,
        val materialRepository: MaterialRepository,
        val tagRepository: TagRepository,
        val fabricRepository: FabricRepository,
        val occasionRepository: OccasionRepository,
    )

@HiltViewModel
class GarmentReviewMetadataViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val importQueueRepository: ImportQueueRepository,
        private val imageRepository: ImageRepository,
        private val garmentRepository: GarmentRepository,
        taxonomy: ReviewTaxonomyRepositories,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val queueItemId = savedStateHandle.toRoute<GarmentReviewMetadataRoute>().queueItemId

        private val loadStateFlow = MutableStateFlow(LoadState())
        private val formStateFlow = MutableStateFlow(GarmentMetadataFormState())
        private val savingStateFlow = MutableStateFlow(SavingState())
        private val retryingStageFlow = MutableStateFlow<ImageRetryStage?>(null)
        private val autoSaveCountdownFlow = MutableStateFlow<Int?>(null)
        private var autoSaveJob: Job? = null

        private val referenceDataFlow =
            combine(
                combine(
                    taxonomy.categoryRepository.observeAll(),
                    taxonomy.brandRepository.observeAll(),
                    taxonomy.colorRepository.observeAll(),
                    taxonomy.materialRepository.observeAll(),
                    taxonomy.tagRepository.observeAll(),
                ) { categories, brands, colors, materials, tags ->
                    TaxonomyCore(categories, brands, colors, materials, tags)
                },
                taxonomy.fabricRepository.observeAll(),
                taxonomy.occasionRepository.observeAll(),
            ) { core, fabrics, occasions ->
                ReferenceData(core.categories, core.brands, core.colors, core.materials, core.tags, fabrics, occasions)
            }

        val uiState: StateFlow<GarmentReviewMetadataUiState> =
            combine(
                combine(loadStateFlow, formStateFlow, savingStateFlow, referenceDataFlow, retryingStageFlow) {
                    load,
                    form,
                    saving,
                    reference,
                    retryingStage,
                    ->
                    CoreUiPieces(load, form, saving, reference, retryingStage)
                },
                autoSaveCountdownFlow,
            ) { core, autoSaveCountdown ->
                buildUiState(core.load, core.form, core.saving, core.reference, core.retryingStage, autoSaveCountdown)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = GarmentReviewMetadataUiState(),
            )

        init {
            viewModelScope.launch { loadQueueItem() }
        }

        private suspend fun loadQueueItem() {
            val item = importQueueRepository.observeQueue().first().firstOrNull { it.id == queueItemId }
            val stagingId = item?.stagingId
            val staged = stagingId?.let { imageRepository.peekStagedImage(it) }
            if (item == null || stagingId == null || staged == null) {
                if (item != null) {
                    importQueueRepository.updateItem(
                        item.copy(status = ImportQueueItemStatus.PENDING, stagingId = null),
                    )
                }
                loadStateFlow.value = LoadState(isLoading = false, needsRestage = true)
                return
            }
            val originalChecksum = staged.variants.firstOrNull { it.type == ImageType.ORIGINAL }?.checksum
            val duplicateGarmentId = originalChecksum?.let { imageRepository.findGarmentIdForChecksum(it) }
            loadStateFlow.value =
                LoadState(
                    isLoading = false,
                    stagingId = stagingId,
                    staged = staged,
                    checksumDuplicateGarmentName = duplicateGarmentId?.let { garmentRepository.getGarment(it)?.name },
                )
            val reference = referenceDataFlow.first()
            val referenceData = reference.toReviewReferenceData()
            val form = autoFillForm(staged.metadataSuggestions, referenceData)
            formStateFlow.value = form
            logMetadataPipelineDiagnostics(context, staged, form, referenceData, reference.categories)
            maybeStartAutoSave(staged, form, reference.categories)
        }

        /** AI Wardrobe Assistant Part 2 — starts the 3-2-1 auto-save countdown
         * only when every required field is genuinely HIGH-confidence or
         * not-applicable (see `AutoSaveGate.kt`). Only evaluated once, right
         * after the initial load's auto-fill — a manual one-tap retry
         * ([onRetryStage]) never re-triggers it, since the user retrying a
         * stage is itself a signal they're already in manual-review mode. */
        private fun maybeStartAutoSave(
            staged: StagedImage,
            form: GarmentMetadataFormState,
            categories: List<Category>,
        ) {
            val eligibility = evaluateAutoSaveEligibility(staged.metadataSuggestions, form.categoryId, categories)
            if (eligibility !is AutoSaveEligibility.Eligible) return
            autoSaveJob =
                viewModelScope.launch {
                    for (secondsLeft in AUTO_SAVE_COUNTDOWN_START_SECONDS downTo 1) {
                        autoSaveCountdownFlow.value = secondsLeft
                        delay(AUTO_SAVE_COUNTDOWN_TICK_MS)
                    }
                    autoSaveCountdownFlow.value = null
                    save(isReviewed = true)
                }
        }

        /** Stops the auto-save countdown without touching any field the AI
         * already filled in — the form stays exactly as auto-filled, the
         * user just reviews/edits/saves it manually from here. */
        fun onCancelAutoSave() {
            autoSaveJob?.cancel()
            autoSaveJob = null
            autoSaveCountdownFlow.value = null
        }

        fun onFormChange(form: GarmentMetadataFormState) {
            formStateFlow.value = form
            val categoryId = form.categoryId
            if (categoryId == null) {
                savingStateFlow.update { it.copy(potentialDuplicates = emptyList()) }
                return
            }
            viewModelScope.launch {
                val duplicates = garmentRepository.findPotentialDuplicates(categoryId, form.primaryColorId, null)
                savingStateFlow.update { it.copy(potentialDuplicates = duplicates) }
            }
        }

        /** Tapping a MEDIUM (or LOW) confidence suggestion chip toggles it:
         * applies the suggested value if it isn't already the form's current
         * value, clears that field back out if it is — never silently
         * ignored either way (Constitution rule 7's editable-suggestion
         * invariant). */
        fun onToggleSuggestion(
            field: MetadataField,
            value: String,
        ) {
            if (!isBindableField(field)) return
            viewModelScope.launch {
                val reference = referenceDataFlow.first().toReviewReferenceData()
                val current = formStateFlow.value
                formStateFlow.value =
                    if (isSuggestionApplied(current, field, value, reference)) {
                        clearSuggestionField(current, field, value, reference)
                    } else {
                        applySuggestion(current, field, value, reference) ?: current
                    }
            }
        }

        /** M10's one-tap retry — redoes only the stages downstream of
         * [stage] and re-derives the form's auto-fill from whatever new
         * metadata suggestions come back, without touching fields the user
         * already edited by hand for anything the new suggestions don't
         * cover. */
        fun onRetryStage(stage: ImageRetryStage) {
            val stagingId = loadStateFlow.value.stagingId ?: return
            viewModelScope.launch {
                retryingStageFlow.value = stage
                runCatching { imageRepository.retryStage(stagingId, stage) }
                    .onSuccess { updated ->
                        loadStateFlow.update { it.copy(staged = updated) }
                        val reference = referenceDataFlow.first().toReviewReferenceData()
                        val autoFilled = autoFillForm(updated.metadataSuggestions, reference)
                        formStateFlow.update { current -> mergeAutoFill(current, autoFilled) }
                    }
                retryingStageFlow.value = null
            }
        }

        fun onSave() = save(isReviewed = true)

        fun onSaveAsDraft() = save(isReviewed = false)

        private fun save(isReviewed: Boolean) {
            autoSaveJob?.cancel()
            autoSaveJob = null
            autoSaveCountdownFlow.value = null
            val form = formStateFlow.value
            val categoryId = form.categoryId ?: return
            val stagingId = loadStateFlow.value.stagingId ?: return
            viewModelScope.launch {
                savingStateFlow.update { it.copy(isSaving = true) }
                setQueueItemStatus(ImportQueueItemStatus.SAVING)
                val materialsById = uiState.value.materials.associateBy { it.id }
                val fabricsById = uiState.value.fabrics.associateBy { it.id }
                val garment = buildNewGarment(form, categoryId, isReviewed, materialsById, fabricsById)
                runCatching {
                    val garmentId = garmentRepository.saveGarment(garment)
                    imageRepository.commitStagedImage(stagingId, garmentId)
                    garmentId
                }.onSuccess { garmentId ->
                    val item = importQueueRepository.observeQueue().first().firstOrNull { it.id == queueItemId }
                    item?.let {
                        importQueueRepository.updateItem(
                            it.copy(status = ImportQueueItemStatus.COMPLETED, savedGarmentId = garmentId),
                        )
                    }
                    savingStateFlow.update { it.copy(isSaving = false, didSave = true) }
                }.onFailure { throwable ->
                    setQueueItemStatus(ImportQueueItemStatus.READY_FOR_REVIEW)
                    savingStateFlow.update { it.copy(isSaving = false, error = throwable.message ?: "Couldn't save.") }
                }
            }
        }

        private suspend fun setQueueItemStatus(status: ImportQueueItemStatus) {
            val item = importQueueRepository.observeQueue().first().firstOrNull { it.id == queueItemId } ?: return
            importQueueRepository.updateItem(item.copy(status = status))
        }
    }

private fun ReferenceData.toReviewReferenceData(): ReviewReferenceData =
    ReviewReferenceData(categories, brands, colors, materials, fabrics, occasions, tags)

/** After a retry, a field the previous auto-fill already set (and the user
 * hasn't since cleared) is refreshed to the new suggestion; fields the user
 * has actively populated with something the new suggestions don't mention
 * at all are left alone rather than blanked out. */
private fun mergeAutoFill(
    current: GarmentMetadataFormState,
    autoFilled: GarmentMetadataFormState,
): GarmentMetadataFormState =
    current.copy(
        categoryId = autoFilled.categoryId ?: current.categoryId,
        brandId = autoFilled.brandId ?: current.brandId,
        primaryColorId = autoFilled.primaryColorId ?: current.primaryColorId,
        secondaryColorId = autoFilled.secondaryColorId ?: current.secondaryColorId,
        materialId = autoFilled.materialId ?: current.materialId,
        fabricId = autoFilled.fabricId ?: current.fabricId,
        patternText = autoFilled.patternText.ifBlank { current.patternText },
        fit = autoFilled.fit ?: current.fit,
        length = autoFilled.length ?: current.length,
        sleeveLength = autoFilled.sleeveLength ?: current.sleeveLength,
        neckline = autoFilled.neckline ?: current.neckline,
        gender = autoFilled.gender ?: current.gender,
        waterproofLevel = autoFilled.waterproofLevel ?: current.waterproofLevel,
        seasons = current.seasons + autoFilled.seasons,
        dressCodes = current.dressCodes + autoFilled.dressCodes,
        occasionIds = current.occasionIds + autoFilled.occasionIds,
    )

private fun buildUiState(
    load: LoadState,
    form: GarmentMetadataFormState,
    saving: SavingState,
    reference: ReferenceData,
    retryingStage: ImageRetryStage?,
    autoSaveCountdownSeconds: Int?,
): GarmentReviewMetadataUiState {
    val staged = load.staged
    return GarmentReviewMetadataUiState(
        isLoading = load.isLoading,
        needsRestage = load.needsRestage,
        previewImagePath =
            staged?.variants?.firstOrNull { it.type == ImageType.CUTOUT }?.filePath
                ?: staged?.variants?.firstOrNull { it.type == ImageType.ORIGINAL }?.filePath,
        usedOriginalFallback = staged != null && staged.backgroundRemovalStatus != BackgroundRemovalStatus.SUCCEEDED,
        checksumDuplicateGarmentName = load.checksumDuplicateGarmentName,
        potentialDuplicates = saving.potentialDuplicates,
        form = form,
        categories = reference.categories,
        brands = reference.brands,
        colors = reference.colors,
        materials = reference.materials,
        tags = reference.tags,
        fabrics = reference.fabrics,
        occasions = reference.occasions,
        isSaving = saving.isSaving,
        saveError = saving.error,
        didSave = saving.didSave,
        variants = staged?.variants.orEmpty(),
        comparisonStages = staged?.comparisonStages.orEmpty(),
        whiteBackgroundImagePath = staged?.variants?.firstOrNull { it.type == ImageType.WHITE_BACKGROUND }?.filePath,
        metadataSuggestions = staged?.metadataSuggestions.orEmpty(),
        aiProcessingSummary = staged?.aiProcessingSummary,
        qualityWarnings =
            staged
                ?.qualityReport
                ?.checks
                .orEmpty()
                .filter { it.verdict != QualityVerdict.PASS },
        occlusionSeverity = staged?.occlusionSeverity,
        reconstructionOutcome = staged?.reconstructionOutcome,
        retryingStage = retryingStage,
        autoSaveCountdownSeconds = autoSaveCountdownSeconds,
    )
}

private fun buildNewGarment(
    form: GarmentMetadataFormState,
    categoryId: com.wardrobe.app.core.model.common.CategoryId,
    isReviewed: Boolean,
    materialsById: Map<com.wardrobe.app.core.model.common.MaterialId, Material>,
    fabricsById: Map<com.wardrobe.app.core.model.common.FabricId, Fabric>,
): Garment {
    val now = Instant.now()
    val material = form.materialId?.let(materialsById::get)
    val fabric = form.fabricId?.let(fabricsById::get)
    return Garment(
        id = GarmentId(0),
        name = form.name.trim().takeUnless { it.isBlank() },
        categoryId = categoryId,
        primaryColorId = form.primaryColorId,
        palette = emptyList(),
        materials = material?.let { listOf(MaterialComposition(it, percentage = null)) } ?: emptyList(),
        tagIds = form.tagIds.toList(),
        seasons = form.seasons,
        dressCodes = form.dressCodes,
        pattern = form.patternText.trim().takeUnless { it.isBlank() },
        fit = form.fit,
        length = form.length,
        sleeveLength = form.sleeveLength,
        warmthRating = null,
        breathabilityRating = null,
        brandId = form.brandId,
        size = form.size.trim().takeUnless { it.isBlank() },
        price = form.priceText.toDoubleOrNull()?.let { Money(it, "GBP") },
        purchaseDate = runCatching { java.time.LocalDate.parse(form.purchaseDate.trim()) }.getOrNull(),
        condition = form.condition,
        careNotes = form.careNotes.trim().takeUnless { it.isBlank() },
        notes = form.notes.trim().takeUnless { it.isBlank() },
        status = form.status,
        isReviewed = isReviewed,
        isFavorite = form.isFavorite,
        isInLaundry = form.isInLaundry,
        images = emptyList(),
        createdAt = now,
        updatedAt = now,
        secondaryColorId = form.secondaryColorId,
        fabrics = fabric?.let { listOf(FabricComposition(it, percentage = null)) } ?: emptyList(),
        occasionIds = form.occasionIds.toList(),
        neckline = form.neckline,
        gender = form.gender,
        waterproofLevel = form.waterproofLevel,
    )
}
