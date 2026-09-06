package com.wardrobe.app.feature.tryon.compare

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.ai.AiResultSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TryOnCompareContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the Cloud tab button reports TryOnCompareTab CLOUD, not On-Device`() {
        val state =
            TryOnCompareUiState(
                isLoading = false,
                bodyPhotoPath = "/tmp/body.webp",
                garmentCutoutPath = "/tmp/cutout.webp",
                onDevice = TryOnRenderTabState.Rendered("/tmp/on-device.webp", 0.4f, AiResultSource.ON_DEVICE),
                cloud = TryOnRenderTabState.Rendered("/tmp/cloud.webp", 0.95f, AiResultSource.CLOUD),
            )
        var selectedTab: TryOnCompareTab? = null

        composeRule.setContent {
            WardrobeTheme { TryOnCompareContent(state) { selectedTab = it } }
        }
        composeRule.onNodeWithText("Cloud").performClick()

        assertEquals(TryOnCompareTab.CLOUD, selectedTab)
    }

    @Test
    fun `a render failure shows its reason rather than a blank screen`() {
        val state =
            TryOnCompareUiState(
                isLoading = false,
                bodyPhotoPath = "/tmp/body.webp",
                garmentCutoutPath = "/tmp/cutout.webp",
                selectedTab = TryOnCompareTab.CLOUD,
                cloud = TryOnRenderTabState.Failed("missing_confidence"),
            )

        composeRule.setContent {
            WardrobeTheme { TryOnCompareContent(state) {} }
        }

        composeRule.onNodeWithText("Couldn't render: missing_confidence").assertExists()
    }

    @Test
    fun `the On-Device tab shows its own confidence when rendered`() {
        val state =
            TryOnCompareUiState(
                isLoading = false,
                bodyPhotoPath = "/tmp/body.webp",
                garmentCutoutPath = "/tmp/cutout.webp",
                selectedTab = TryOnCompareTab.ON_DEVICE,
                onDevice = TryOnRenderTabState.Rendered("/tmp/on-device.webp", 0.6f, AiResultSource.ON_DEVICE),
            )

        composeRule.setContent {
            WardrobeTheme { TryOnCompareContent(state) {} }
        }

        composeRule.onNodeWithText("Source: On-Device").assertExists()
        composeRule.onNodeWithText("Confidence: 60%").assertExists()
    }
}
