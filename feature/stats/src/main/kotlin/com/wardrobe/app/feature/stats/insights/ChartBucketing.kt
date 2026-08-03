package com.wardrobe.app.feature.stats.insights

import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.feature.stats.common.MonthlyWeeklyBuckets
import com.wardrobe.app.feature.stats.common.charts.BarChartEntry
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** [buildCharts]'s heatmap-bucketing and season/dress-code label helpers,
 * split into their own file purely to stay under detekt's
 * `TooManyFunctions` ceiling on `InsightsBuilders.kt` — `internal`, not
 * `private`, since a top-level `private` declaration is file-scoped in
 * Kotlin and `buildCharts` still needs to call these from the sibling file. */

private const val RECENT_WEEKS_SHOWN = 8
private val MONTH_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val WEEK_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

internal fun computeMonthlyWeeklyBuckets(heatmap: List<WearHeatmapDay>): MonthlyWeeklyBuckets =
    MonthlyWeeklyBuckets(
        monthly = bucketByMonth(heatmap),
        weekly = bucketByWeek(heatmap).takeLast(RECENT_WEEKS_SHOWN),
    )

private fun bucketByMonth(heatmap: List<WearHeatmapDay>): List<BarChartEntry> =
    heatmap
        .groupBy { YearMonth.from(it.date) }
        .toSortedMap()
        .map { (month, days) ->
            BarChartEntry(month.atDay(1).format(MONTH_LABEL_FORMATTER), days.sumOf { it.wearCount })
        }

private fun bucketByWeek(heatmap: List<WearHeatmapDay>): List<BarChartEntry> {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return heatmap
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }
        .toSortedMap()
        .map { (weekStart, days) -> BarChartEntry(weekStart.format(WEEK_LABEL_FORMATTER), days.sumOf { it.wearCount }) }
}

internal fun Season.shortLabel(): String =
    when (this) {
        Season.SPRING -> "Spring"
        Season.SUMMER -> "Summer"
        Season.AUTUMN -> "Autumn"
        Season.WINTER -> "Winter"
    }

internal fun DressCode.shortLabel(): String =
    when (this) {
        DressCode.CASUAL -> "Casual"
        DressCode.SMART_CASUAL -> "Smart"
        DressCode.BUSINESS -> "Business"
        DressCode.FORMAL -> "Formal"
        DressCode.ATHLETIC -> "Athletic"
        DressCode.LOUNGE -> "Lounge"
    }
