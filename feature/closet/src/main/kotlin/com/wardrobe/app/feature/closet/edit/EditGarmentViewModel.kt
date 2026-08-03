package com.wardrobe.app.feature.closet.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.feature.closet.navigation.EditGarmentRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

@HiltViewModel
class EditGarmentViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val garmentRepository: GarmentRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        colorRepository: ColorRepository,
        tagRepository: TagRepository,
    ) : ViewModel() {
        private val garmentId = GarmentId(savedStateHandle.toRoute<EditGarmentRoute>().garmentId)

        private var originalGarment: Garment? = null
        private val loadStateFlow = MutableStateFlow(LoadState())
        private val savingStateFlow = MutableStateFlow(SavingState())

        private data class LoadState(
            val isLoading: Boolean = true,
            val notFound: Boolean = false,
            val form: EditGarmentFormState = EditGarmentFormState(),
        )

        private data class SavingState(
            val isSaving: Boolean = false,
            val error: String? = null,
            val didSave: Boolean = false,
        )

        private data class ReferenceData(
            val categories: List<com.wardrobe.app.core.model.garment.Category>,
            val brands: List<com.wardrobe.app.core.model.garment.Brand>,
            val colors: List<com.wardrobe.app.core.model.garment.Color>,
            val tags: List<com.wardrobe.app.core.model.garment.Tag>,
        )

        private val referenceDataFlow =
            combine(
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                colorRepository.observeAll(),
                tagRepository.observeAll(),
            ) { categories, brands, colors, tags -> ReferenceData(categories, brands, colors, tags) }

        val uiState: StateFlow<EditGarmentUiState> =
            combine(loadStateFlow, savingStateFlow, referenceDataFlow) { load, saving, reference ->
                EditGarmentUiState(
                    isLoading = load.isLoading,
                    notFound = load.notFound,
                    form = load.form,
                    categories = reference.categories,
                    brands = reference.brands,
                    colors = reference.colors,
                    tags = reference.tags,
                    isSaving = saving.isSaving,
                    saveError = saving.error,
                    didSave = saving.didSave,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = EditGarmentUiState(),
            )

        init {
            viewModelScope.launch {
                val garment = garmentRepository.getGarment(garmentId)
                originalGarment = garment
                loadStateFlow.value =
                    if (garment == null) {
                        LoadState(isLoading = false, notFound = true)
                    } else {
                        LoadState(isLoading = false, form = garment.toFormState())
                    }
            }
        }

        fun onFormChange(form: EditGarmentFormState) {
            loadStateFlow.update { it.copy(form = form) }
        }

        fun onSave() {
            val original = originalGarment ?: return
            val form = loadStateFlow.value.form
            viewModelScope.launch {
                savingStateFlow.value = SavingState(isSaving = true)
                val updated =
                    original.copy(
                        name = form.name.trim().takeUnless { it.isBlank() },
                        categoryId = form.categoryId ?: original.categoryId,
                        brandId = form.brandId,
                        primaryColorId = form.primaryColorId,
                        size = form.size.trim().takeUnless { it.isBlank() },
                        price =
                            form.priceText.toDoubleOrNull()?.let {
                                Money(
                                    it,
                                    original.price?.currencyCode ?: "GBP",
                                )
                            },
                        condition = form.condition,
                        careNotes = form.careNotes.trim().takeUnless { it.isBlank() },
                        purchaseDate = runCatching { java.time.LocalDate.parse(form.purchaseDate.trim()) }.getOrNull(),
                        notes = form.notes.trim().takeUnless { it.isBlank() },
                        seasons = form.seasons,
                        dressCodes = form.dressCodes,
                        tagIds = form.tagIds.toList(),
                        // Completing an edit is what clears "Needs Review"/"Continue Editing"
                        // status for a garment saved as a draft via Add-to-Wardrobe.
                        isReviewed = true,
                        updatedAt = java.time.Instant.now(),
                    )
                runCatching { garmentRepository.updateGarment(updated) }
                    .onSuccess { savingStateFlow.value = SavingState(didSave = true) }
                    .onFailure { throwable ->
                        savingStateFlow.value = SavingState(error = throwable.message ?: "Couldn't save changes.")
                    }
            }
        }
    }

private fun Garment.toFormState() =
    EditGarmentFormState(
        name = name.orEmpty(),
        categoryId = categoryId,
        brandId = brandId,
        primaryColorId = primaryColorId,
        size = size.orEmpty(),
        priceText = price?.amount?.toString().orEmpty(),
        condition = condition,
        careNotes = careNotes.orEmpty(),
        purchaseDate = purchaseDate?.toString().orEmpty(),
        notes = notes.orEmpty(),
        seasons = seasons,
        dressCodes = dressCodes,
        tagIds = tagIds.toSet(),
    )
