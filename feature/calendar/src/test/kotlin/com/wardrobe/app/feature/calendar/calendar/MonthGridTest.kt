package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class MonthGridTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping a day cell selects that date`() {
        val month = YearMonth.of(2026, 8)
        val target = LocalDate.of(2026, 8, 15)
        val days =
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                DayCellUiModel(date = date, isCurrentMonth = true, isToday = false, wornCount = 0, plannedCount = 0)
            }
        var selected: LocalDate? = null

        composeTestRule.setContent {
            WardrobeTheme {
                MonthGrid(days = days, selectedDate = month.atDay(1), onSelectDate = { selected = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("15").performClick()

        assert(selected == target)
    }

    @Test
    fun `a day with logged wear announces it in its content description`() {
        val month = YearMonth.of(2026, 8)
        val days =
            listOf(
                DayCellUiModel(
                    date = month.atDay(1),
                    isCurrentMonth = true,
                    isToday = false,
                    wornCount = 1,
                    plannedCount = 0,
                ),
            )

        composeTestRule.setContent {
            WardrobeTheme { MonthGrid(days = days, selectedDate = month.atDay(1), onSelectDate = {}) }
        }

        composeTestRule.onNodeWithContentDescription("1, 1 logged").assertExists()
    }
}
