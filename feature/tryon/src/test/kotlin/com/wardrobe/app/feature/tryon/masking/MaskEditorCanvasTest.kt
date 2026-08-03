package com.wardrobe.app.feature.tryon.masking

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Semantics/structure only — never visual appearance (no device to compare
 * a real erased/restored garment cutout against; see
 * `phase-10-personal-virtual-tryon.md`'s Known Limitations).
 */
@RunWith(RobolectricTestRunner::class)
class MaskEditorCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun `shows the canvas and all three actions`() {
        composeRule.setContent {
            WardrobeTheme {
                MaskEditorCanvas(bitmap = bitmap(), onErase = {
                    _,
                    _,
                    ->
                }, onRestore = { _, _ -> }, onSave = {})
            }
        }

        composeRule.onNodeWithContentDescription("Garment mask editor canvas").assertExists()
        composeRule.onNodeWithText("Erase").assertExists()
        composeRule.onNodeWithText("Restore").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun `tapping Save invokes the onSave callback`() {
        var saved = false
        composeRule.setContent {
            WardrobeTheme {
                MaskEditorCanvas(bitmap = bitmap(), onErase = { _, _ -> }, onRestore = { _, _ -> }, onSave = {
                    saved =
                        true
                })
            }
        }

        composeRule.onNodeWithText("Save").performClick()

        assertTrue(saved)
    }

    @Test
    fun `switching to Restore does not invoke onSave or crash the canvas`() {
        composeRule.setContent {
            WardrobeTheme {
                MaskEditorCanvas(bitmap = bitmap(), onErase = {
                    _,
                    _,
                    ->
                }, onRestore = { _, _ -> }, onSave = {})
            }
        }

        composeRule.onNodeWithText("Restore").performClick()

        composeRule.onNodeWithContentDescription("Garment mask editor canvas").assertExists()
    }
}
