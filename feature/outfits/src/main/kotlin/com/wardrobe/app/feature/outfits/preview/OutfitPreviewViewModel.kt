package com.wardrobe.app.feature.outfits.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.feature.outfits.navigation.OutfitPreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewLayerUiModel(
    val slot: OutfitSlot,
    val garmentId: Long,
    val title: String,
    val imagePath: String?,
)

data class OutfitPreviewUiState(
    val isLoading: Boolean = true,
    val layers: List<PreviewLayerUiModel> = emptyList(),
)

@HiltViewModel
class OutfitPreviewViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val garmentRepository: GarmentRepository,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val garmentIds =
            savedStateHandle.toRoute<OutfitPreviewRoute>().garmentIds.split(",").mapNotNull {
                it.toLongOrNull()
            }

        private val mutableUiState = MutableStateFlow(OutfitPreviewUiState())
        val uiState: StateFlow<OutfitPreviewUiState> = mutableUiState.asStateFlow()

        init {
            viewModelScope.launch {
                val garments =
                    garmentRepository
                        .observeGarments(GarmentFilter(status = null))
                        .first()
                        .filter { it.id in garmentIds.map(::GarmentId) }
                val categories = categoryRepository.observeAll().first().associateBy { it.id }
                val layers =
                    garments.mapNotNull { garment ->
                        val slot =
                            categories[garment.categoryId]?.name?.let(OutfitSlot::classify) ?: return@mapNotNull null
                        val imagePath =
                            garment.images.firstOrNull { it.type == ImageType.CUTOUT }?.filePath
                                ?: garment.images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
                        PreviewLayerUiModel(slot, garment.id.value, garment.name ?: "Item", imagePath)
                    }
                mutableUiState.value = OutfitPreviewUiState(isLoading = false, layers = layers)
            }
        }
    }
