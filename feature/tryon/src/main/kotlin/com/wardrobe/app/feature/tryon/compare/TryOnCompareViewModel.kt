package com.wardrobe.app.feature.tryon.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.VirtualTryOnRenderRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.VirtualTryOnRenderOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GARMENT_ID_ARG = "garmentId"

/**
 * M12's Try-On Review comparison viewer — requests an explicitly forced
 * on-device render alongside the normal auto-routed one (whatever the
 * user's `VIRTUAL_TRY_ON` provider config actually produces), so "Original /
 * On-Device / Cloud" is never a lie: the Cloud tab shows exactly what the
 * on-device fallback returned if cloud isn't configured, labeled by the
 * real [com.wardrobe.app.core.model.ai.AiResultSource] the repository
 * reports back rather than an assumption. Never persists anything — every
 * render is a scratch preview file, and neither the body photo nor the
 * garment's own cutout is ever overwritten.
 */
@HiltViewModel
class TryOnCompareViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val garmentRepository: GarmentRepository,
        private val bodyProfileRepository: BodyProfileRepository,
        private val virtualTryOnRenderRepository: VirtualTryOnRenderRepository,
    ) : ViewModel() {
        private val garmentId = GarmentId(checkNotNull(savedStateHandle.get<Long>(GARMENT_ID_ARG)))
        private val mutableUiState = MutableStateFlow(TryOnCompareUiState())
        val uiState: StateFlow<TryOnCompareUiState> = mutableUiState.asStateFlow()

        init {
            load()
        }

        fun selectTab(tab: TryOnCompareTab) = mutableUiState.update { it.copy(selectedTab = tab) }

        private fun load() {
            viewModelScope.launch {
                val garment = garmentRepository.getGarment(garmentId)
                val bodyProfile = bodyProfileRepository.observeBodyProfile().first()
                val bodyPhotoPath = bodyProfile?.photos?.firstOrNull { it.pose == BodyPose.NEUTRAL }?.filePath
                val garmentCutoutPath = garment?.images?.firstOrNull { it.type == ImageType.CUTOUT }?.filePath

                if (bodyPhotoPath == null || garmentCutoutPath == null) {
                    mutableUiState.update { it.copy(isLoading = false, missingProfileOrGarment = true) }
                    return@launch
                }

                mutableUiState.update {
                    it.copy(isLoading = false, bodyPhotoPath = bodyPhotoPath, garmentCutoutPath = garmentCutoutPath)
                }
                renderTab(bodyPhotoPath, garmentCutoutPath, forceOnDevice = true) { state ->
                    mutableUiState.update { it.copy(onDevice = state) }
                }
                renderTab(bodyPhotoPath, garmentCutoutPath, forceOnDevice = false) { state ->
                    mutableUiState.update { it.copy(cloud = state) }
                }
            }
        }

        private suspend fun renderTab(
            bodyPhotoPath: String,
            garmentCutoutPath: String,
            forceOnDevice: Boolean,
            update: (TryOnRenderTabState) -> Unit,
        ) {
            val outcome =
                virtualTryOnRenderRepository.render(
                    bodyPhotoPath,
                    garmentCutoutPath,
                    forceOnDevice = forceOnDevice,
                )
            update(outcome.toTabState())
        }
    }

private fun VirtualTryOnRenderOutcome.toTabState(): TryOnRenderTabState =
    when (this) {
        is VirtualTryOnRenderOutcome.Success -> TryOnRenderTabState.Rendered(renderedImagePath, confidence, source)
        is VirtualTryOnRenderOutcome.Failure -> TryOnRenderTabState.Failed(reason)
    }
