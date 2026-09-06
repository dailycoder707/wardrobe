package com.wardrobe.app.feature.tryon.compare

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.ai.AiResultSource

/** One of the comparison viewer's three tabs (M12 acceptance criteria,
 * Try-On Review: "Original / On-device / Cloud, allow comparison"). */
enum class TryOnCompareTab { ORIGINAL, ON_DEVICE, CLOUD }

@Immutable
sealed interface TryOnRenderTabState {
    data object Loading : TryOnRenderTabState

    data class Rendered(
        val imagePath: String,
        val confidence: Float?,
        val source: AiResultSource,
    ) : TryOnRenderTabState

    data class Failed(
        val reason: String,
    ) : TryOnRenderTabState
}

@Immutable
data class TryOnCompareUiState(
    val isLoading: Boolean = true,
    val bodyPhotoPath: String? = null,
    val garmentCutoutPath: String? = null,
    val selectedTab: TryOnCompareTab = TryOnCompareTab.ORIGINAL,
    val onDevice: TryOnRenderTabState = TryOnRenderTabState.Loading,
    val cloud: TryOnRenderTabState = TryOnRenderTabState.Loading,
    val missingProfileOrGarment: Boolean = false,
)
