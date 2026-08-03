package com.wardrobe.app.feature.tryon.render

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.TryOnPlacementRepository
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.ClothingDepth
import com.wardrobe.app.core.tryon.rendering.sortForRender
import com.wardrobe.app.feature.tryon.navigation.TryOnRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads the body profile plus one [TryOnLayerUiModel] per garment (dual
 * input — a real, persisted [TryOnRoute.outfitId], or a raw
 * [TryOnRoute.garmentIds] list for Today's Recommendation's not-yet-saved
 * scored outfit), sorted for render via `sortForRender`. Each layer's
 * gesture updates are persisted immediately through
 * [TryOnPlacementRepository] — this screen never batches or debounces a
 * write, since a placement template row is a single cheap upsert.
 */
@HiltViewModel
class TryOnViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val bodyProfileRepository: BodyProfileRepository,
        private val garmentRepository: GarmentRepository,
        private val categoryRepository: CategoryRepository,
        private val outfitRepository: OutfitRepository,
        private val placementRepository: TryOnPlacementRepository,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<TryOnRoute>()
        private val mutableUiState = MutableStateFlow(TryOnUiState())
        val uiState: StateFlow<TryOnUiState> = mutableUiState.asStateFlow()

        init {
            // A `collectLatest` over the body profile (not a one-shot `first()`)
            // so completing guided capture from this same screen's "needs body
            // profile" prompt refreshes it reactively — no fresh route
            // navigation required to pick up the newly created profile.
            viewModelScope.launch {
                bodyProfileRepository.observeBodyProfile().collectLatest { profile -> load(profile) }
            }
        }

        private suspend fun load(profile: com.wardrobe.app.core.model.tryon.BodyProfile?) {
            if (profile == null) {
                mutableUiState.value = TryOnUiState(isLoading = false, needsBodyProfile = true)
                return
            }
            val categories = categoryRepository.observeAll().first().associateBy { it.id }
            val layers =
                resolveGarmentIds().mapNotNull { garmentId -> buildLayer(profile.id, garmentId, categories) }
            mutableUiState.value =
                TryOnUiState(
                    isLoading = false,
                    backgroundPhotoPath = profile.photos.firstOrNull { it.pose == BodyPose.NEUTRAL }?.filePath,
                    layers = sortForRender(layers) { it.slot },
                )
        }

        private suspend fun resolveGarmentIds(): List<GarmentId> {
            val outfitId = route.outfitId
            return if (outfitId != null) {
                outfitRepository
                    .getOutfit(OutfitId(outfitId))
                    ?.garments
                    .orEmpty()
                    .map { it.garmentId }
            } else {
                route.garmentIds
                    .orEmpty()
                    .split(",")
                    .mapNotNull { it.toLongOrNull() }
                    .map(::GarmentId)
            }
        }

        private suspend fun buildLayer(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
            categories: Map<com.wardrobe.app.core.model.common.CategoryId, Category>,
        ): TryOnLayerUiModel? {
            val garment = garmentRepository.getGarment(garmentId)
            val slot = garment?.let { categories[it.categoryId]?.name?.let(OutfitSlot::classify) }
            return if (garment == null || slot == null) {
                null
            } else {
                val template = placementRepository.defaultTemplateFor(bodyProfileId, garmentId)
                val imagePath =
                    garment.images.firstOrNull { it.type == ImageType.CUTOUT }?.filePath
                        ?: garment.images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
                TryOnLayerUiModel(
                    garmentId = garmentId,
                    bodyProfileId = bodyProfileId,
                    slot = slot,
                    depth = ClothingDepth.defaultDepthFor(slot),
                    title = garment.name ?: "Item",
                    imagePath = imagePath,
                    template = template,
                )
            }
        }

        /** Called on every gesture delta from the layer's own
         * `detectTransformGestures` — flips `isUserAdjusted` permanently
         * true (the confidence chip's "gone forever until Reset" contract)
         * and marks this template as the most recently used one. */
        fun onLayerTransformed(
            garmentId: GarmentId,
            offsetXFraction: Float,
            offsetYFraction: Float,
            scale: Float,
            rotationDegrees: Float,
        ) {
            val current = mutableUiState.value.layers.firstOrNull { it.garmentId == garmentId } ?: return
            val updatedTemplate =
                current.template.copy(
                    offsetXFraction = offsetXFraction,
                    offsetYFraction = offsetYFraction,
                    scale = scale,
                    rotationDegrees = rotationDegrees,
                    isUserAdjusted = true,
                )
            replaceLayer(garmentId, updatedTemplate)
            viewModelScope.launch {
                placementRepository.saveGarmentPlacementTemplate(updatedTemplate)
                placementRepository.markTemplateUsed(updatedTemplate.id)
            }
        }

        /** The "Reset to Auto Placement" action's backing call. */
        fun onResetToAutoPlacement(garmentId: GarmentId) {
            val current = mutableUiState.value.layers.firstOrNull { it.garmentId == garmentId } ?: return
            viewModelScope.launch {
                val reset = placementRepository.resetToAutoPlacement(current.template.id)
                replaceLayer(garmentId, reset)
            }
        }

        private fun replaceLayer(
            garmentId: GarmentId,
            template: com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate,
        ) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    layers =
                        mutableUiState.value.layers.map {
                            if (it.garmentId == garmentId) it.copy(template = template) else it
                        },
                )
        }
    }
