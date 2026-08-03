package com.wardrobe.app.feature.capture.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.MaterialComposition
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.feature.capture.navigation.GarmentReviewMetadataRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class GarmentReviewMetadataViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val importQueueRepository: ImportQueueRepository,
        private val imageRepository: ImageRepository,
        private val garmentRepository: GarmentRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        colorRepository: ColorRepository,
        materialRepository: MaterialRepository,
        tagRepository: TagRepository,
    ) : ViewModel() {
        private val queueItemId = savedStateHandle.toRoute<GarmentReviewMetadataRoute>().queueItemId

        private val loadStateFlow = MutableStateFlow(LoadState())
        private val formStateFlow = MutableStateFlow(GarmentMetadataFormState())
        private val savingStateFlow = MutableStateFlow(SavingState())

        private data class LoadState(
            val isLoading: Boolean = true,
            val needsRestage: Boolean = false,
            val stagingId: String? = null,
            val previewImagePath: String? = null,
            val usedOriginalFallback: Boolean = false,
            val checksumDuplicateGarmentName: String? = null,
        )

        private data class SavingState(
            val isSaving: Boolean = false,
            val error: String? = null,
            val didSave: Boolean = false,
            val potentialDuplicates: List<Garment> = emptyList(),
        )

        private data class ReferenceData(
            val categories: List<Category>,
            val brands: List<Brand>,
            val colors: List<Color>,
            val materials: List<Material>,
            val tags: List<Tag>,
        )

        private val referenceDataFlow =
            combine(
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                colorRepository.observeAll(),
                materialRepository.observeAll(),
                tagRepository.observeAll(),
            ) { categories, brands, colors, materials, tags ->
                ReferenceData(categories, brands, colors, materials, tags)
            }

        val uiState: StateFlow<GarmentReviewMetadataUiState> =
            combine(loadStateFlow, formStateFlow, savingStateFlow, referenceDataFlow) { load, form, saving, reference ->
                GarmentReviewMetadataUiState(
                    isLoading = load.isLoading,
                    needsRestage = load.needsRestage,
                    previewImagePath = load.previewImagePath,
                    usedOriginalFallback = load.usedOriginalFallback,
                    checksumDuplicateGarmentName = load.checksumDuplicateGarmentName,
                    potentialDuplicates = saving.potentialDuplicates,
                    form = form,
                    categories = reference.categories,
                    brands = reference.brands,
                    colors = reference.colors,
                    materials = reference.materials,
                    tags = reference.tags,
                    isSaving = saving.isSaving,
                    saveError = saving.error,
                    didSave = saving.didSave,
                )
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
                    previewImagePath =
                        staged.variants.firstOrNull { it.type == ImageType.CUTOUT }?.filePath
                            ?: staged.variants.firstOrNull { it.type == ImageType.ORIGINAL }?.filePath,
                    usedOriginalFallback = staged.backgroundRemovalStatus != BackgroundRemovalStatus.SUCCEEDED,
                    checksumDuplicateGarmentName = duplicateGarmentId?.let { garmentRepository.getGarment(it)?.name },
                )
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

        fun onSave() = save(isReviewed = true)

        fun onSaveAsDraft() = save(isReviewed = false)

        private fun save(isReviewed: Boolean) {
            val form = formStateFlow.value
            val categoryId = form.categoryId ?: return
            val stagingId = loadStateFlow.value.stagingId ?: return
            viewModelScope.launch {
                savingStateFlow.update { it.copy(isSaving = true) }
                setQueueItemStatus(ImportQueueItemStatus.SAVING)
                val materialsById = uiState.value.materials.associateBy { it.id }
                val garment = buildNewGarment(form, categoryId, isReviewed, materialsById)
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

private fun buildNewGarment(
    form: GarmentMetadataFormState,
    categoryId: com.wardrobe.app.core.model.common.CategoryId,
    isReviewed: Boolean,
    materialsById: Map<com.wardrobe.app.core.model.common.MaterialId, Material>,
): Garment {
    val now = Instant.now()
    val material = form.materialId?.let(materialsById::get)
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
        pattern = null,
        fit = null,
        length = null,
        sleeveLength = null,
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
    )
}
