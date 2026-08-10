package com.wardrobe.app.feature.tryon.render

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.GarmentPlacementTemplateId
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.tryon.ClothingDepth
import com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.model.tryon.PlacementTemplateType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Layer count/render-order/semantics only — never visual appearance (no
 * device, no real photos to compare against here; see
 * `phase-10-personal-virtual-tryon.md`'s Known Limitations).
 */
@RunWith(RobolectricTestRunner::class)
class TryOnCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun template(
        id: Long,
        isUserAdjusted: Boolean = false,
    ) = GarmentPlacementTemplate(
        id = GarmentPlacementTemplateId(id),
        bodyProfileId = BodyProfileId(1),
        garmentId = GarmentId(id),
        templateType = PlacementTemplateType.DEFAULT,
        customName = null,
        offsetXFraction = 0.5f,
        offsetYFraction = 0.3f,
        scale = 1f,
        rotationDegrees = 0f,
        isUserAdjusted = isUserAdjusted,
        placementSource = MeasurementSource.DEFAULT_HEURISTIC,
        lastUsedAt = null,
    )

    private fun layer(
        id: Long,
        slot: OutfitSlot,
        title: String,
        isUserAdjusted: Boolean = false,
    ) = TryOnLayerUiModel(
        garmentId = GarmentId(id),
        bodyProfileId = BodyProfileId(1),
        slot = slot,
        depth = ClothingDepth.defaultDepthFor(slot),
        title = title,
        imagePath = null,
        template = template(id, isUserAdjusted),
    )

    private fun setCanvasContent(state: TryOnUiState) {
        composeRule.setContent {
            WardrobeTheme {
                TryOnCanvas(
                    state = state,
                    onLayerTransformed = { _, _, _, _, _ -> },
                    onResetToAutoPlacement = {},
                    onEditMask = {},
                    onCompareWithCloud = {},
                )
            }
        }
    }

    @Test
    fun `renders exactly one layer per garment in the state`() {
        setCanvasContent(
            TryOnUiState(
                isLoading = false,
                layers = listOf(layer(1, OutfitSlot.TOP, "Shirt"), layer(2, OutfitSlot.BOTTOM, "Jeans")),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Shirt").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Jeans").assertCountEquals(1)
    }

    @Test
    fun `an un-adjusted template shows the auto-placed confidence chip`() {
        setCanvasContent(
            TryOnUiState(isLoading = false, layers = listOf(layer(1, OutfitSlot.TOP, "Shirt", isUserAdjusted = false))),
        )

        composeRule.onNodeWithText("Auto-placed — drag to adjust").assertExists()
    }

    @Test
    fun `a user-adjusted template never shows the confidence chip`() {
        setCanvasContent(
            TryOnUiState(isLoading = false, layers = listOf(layer(1, OutfitSlot.TOP, "Shirt", isUserAdjusted = true))),
        )

        composeRule.onNodeWithText("Auto-placed — drag to adjust").assertDoesNotExist()
    }

    @Test
    fun `every layer exposes a Reset to Auto Placement action`() {
        setCanvasContent(
            TryOnUiState(
                isLoading = false,
                layers = listOf(layer(1, OutfitSlot.TOP, "Shirt"), layer(2, OutfitSlot.OUTERWEAR, "Jacket")),
            ),
        )

        composeRule.onAllNodesWithText("Reset to Auto Placement").assertCountEquals(2)
    }

    @Test
    fun `every layer exposes an Edit Mask action`() {
        setCanvasContent(
            TryOnUiState(
                isLoading = false,
                layers = listOf(layer(1, OutfitSlot.TOP, "Shirt"), layer(2, OutfitSlot.OUTERWEAR, "Jacket")),
            ),
        )

        composeRule.onAllNodesWithText("Edit Mask").assertCountEquals(2)
    }

    @Test
    fun `each layer's semantics content description matches its garment title`() {
        setCanvasContent(TryOnUiState(isLoading = false, layers = listOf(layer(1, OutfitSlot.TOP, "Blue Shirt"))))

        composeRule.onNodeWithContentDescription("Blue Shirt").assertExists()
    }
}
