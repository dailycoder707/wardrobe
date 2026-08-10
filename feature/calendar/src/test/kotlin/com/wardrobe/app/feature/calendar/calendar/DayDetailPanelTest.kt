package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.wear.WearEventStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DayDetailPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun noopActions(
        onGetRecommendation: () -> Unit = {},
        onLogWear: () -> Unit = {},
        onOccasionSelected: (OccasionId?) -> Unit = {},
    ) = DayDetailActions(
        onLogWear = onLogWear,
        onClearDay = {},
        onDuplicateDay = {},
        onScheduleRecurring = {},
        onRescheduleEvent = {},
        onConfirmWorn = {},
        onDeleteEvent = {},
        onGetRecommendation = onGetRecommendation,
        onReplaceEvent = {},
        onOccasionSelected = onOccasionSelected,
    )

    @Test
    fun `an empty day shows the honest empty state with both real actions`() {
        var recommendationRequested = false
        var chooseOutfitRequested = false

        composeTestRule.setContent {
            WardrobeTheme {
                DayDetailPanel(
                    date = LocalDate.of(2026, 8, 10),
                    events = emptyList(),
                    actions =
                        noopActions(
                            onGetRecommendation = { recommendationRequested = true },
                            onLogWear = { chooseOutfitRequested = true },
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText("No outfit planned").assertExists()
        composeTestRule.onNodeWithText("Get recommendation").performClick()
        composeTestRule.onNodeWithText("Choose outfit").performClick()

        assert(recommendationRequested)
        assert(chooseOutfitRequested)
    }

    @Test
    fun `selecting an occasion chip reports the chosen occasion`() {
        val workOccasion = Occasion(OccasionId(1), "Work")
        var selected: OccasionId? = null

        composeTestRule.setContent {
            WardrobeTheme {
                DayDetailPanel(
                    date = LocalDate.of(2026, 8, 10),
                    events = emptyList(),
                    actions = noopActions(onOccasionSelected = { selected = it }),
                    availableOccasions = listOf(workOccasion),
                    selectedOccasionId = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Work").performClick()

        assert(selected == workOccasion.id)
    }

    @Test
    fun `a planned event worn recently shows an honest wear-history badge`() {
        val event =
            WearEventUiModel(
                id = 1L,
                date = LocalDate.of(2026, 8, 10),
                title = "Blue Sweater",
                thumbnailPath = null,
                isOutfit = false,
                sourceId = 5L,
                status = WearEventStatus.PLANNED,
                occasionName = null,
                wornRecently = true,
            )

        composeTestRule.setContent {
            WardrobeTheme {
                DayDetailPanel(date = event.date, events = listOf(event), actions = noopActions())
            }
        }

        composeTestRule.onNodeWithText("Worn recently").assertExists()
    }
}
