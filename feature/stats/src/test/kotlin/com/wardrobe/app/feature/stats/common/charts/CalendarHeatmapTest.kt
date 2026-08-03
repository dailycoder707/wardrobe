package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(RobolectricTestRunner::class)
class CalendarHeatmapTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a day with wears exposes its count in its content description`() {
        val start = LocalDate.of(2026, 6, 1)
        val end = LocalDate.of(2026, 6, 7)
        val wornDate = LocalDate.of(2026, 6, 3)
        val cells = listOf(HeatmapCellUiModel(wornDate, wearCount = 2))

        composeTestRule.setContent {
            WardrobeTheme { CalendarHeatmap(cells = cells, rangeStart = start, rangeEnd = end) }
        }

        val formattedDate = wornDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        composeTestRule.onNodeWithContentDescription("$formattedDate: 2 worn").assertExists()
    }
}
