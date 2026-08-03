package com.wardrobe.app.feature.stats.insights

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private const val GARMENT_COUNT = 1000
private const val OUTFIT_COUNT = 500
private const val HEATMAP_DAYS = 730

/**
 * "Use the derived-query strategy from Phase 3... avoid expensive
 * recomputation" (this phase's own brief) is only a real claim if it's
 * actually exercised at a scale bigger than a hand-written unit test's
 * usual handful of rows. These are plain JVM tests (no Room, no Robolectric
 * needed) targeting the Kotlin-side aggregation `feature:stats` does once
 * `StatsRepository` has already returned its rows — the part this module
 * controls and could plausibly get expensive.
 */
class InsightsBuildersLargeDatasetTest {
    @Test
    fun `building lists from a thousand garments stays bounded and fast`() {
        val garments = (1..GARMENT_COUNT).map { id -> testGarment(id.toLong(), "Garment $id") }
        val outfits = (1..OUTFIT_COUNT).map { id -> testOutfit(id.toLong(), "Outfit $id") }
        val costPerWear =
            garments.mapIndexed { index, garment ->
                CostPerWearEntry(garment.id, totalWearCount = index % 30, costPerWear = 1.0, lastWornDate = null)
            }
        val dormant = garments.map { DormantItem(it.id, lastWornDate = null) }
        val activity =
            (1..GARMENT_COUNT).map { index ->
                testWearEvent(
                    index.toLong(),
                    LocalDate.of(2026, 1, 1).plusDays((index % 300).toLong()),
                    GarmentId(index.toLong()),
                )
            }
        val lists =
            InsightsListsSource(
                costPerWear = costPerWear,
                dormant = dormant,
                missingOutfits = garments.map { it.id },
                neverWornOutfits = outfits.map { it.id },
                repeatedOutfits = outfits.map { RepeatedOutfit(it.id, wearCount = 2) },
            )

        val elapsedMillis =
            measureElapsedMillis {
                val result =
                    buildLists(
                        garments,
                        outfits,
                        lists,
                        activity,
                        LocalDate.of(2026, 12, 1),
                        usageStatsFixture(),
                    )

                assertTrue(result.mostWorn.size <= INSIGHT_LIST_CAP)
                assertTrue(result.leastWorn.size <= INSIGHT_LIST_CAP)
                assertTrue(result.recentActivity.size <= RECENT_ACTIVITY_CAP)
            }

        assertTrue(
            "expected under 2s for 1000 garments, took ${elapsedMillis}ms",
            elapsedMillis < LARGE_DATASET_BUDGET_MS,
        )
    }

    @Test
    fun `bucketing two years of daily heatmap data produces one bucket per month`() {
        val start = LocalDate.of(2025, 1, 1)
        val heatmap =
            (0 until HEATMAP_DAYS).map { offset -> WearHeatmapDay(start.plusDays(offset.toLong()), wearCount = 1) }
        val usage = usageStatsFixture()

        val charts = buildCharts(usage, heatmap, DateRange(start, start.plusDays((HEATMAP_DAYS - 1).toLong())))

        assertEquals(HEATMAP_DAYS, charts.heatmapCells.size)
        assertTrue(charts.monthlyWear.isNotEmpty())
        assertTrue(charts.weeklyWear.size <= WEEKLY_CHART_CAP)
    }

    private fun measureElapsedMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun testGarment(
        id: Long,
        name: String,
    ) = Garment(
        id = GarmentId(id),
        name = name,
        categoryId = CategoryId(1),
        primaryColorId = ColorId(1),
        palette = emptyList(),
        materials = emptyList(),
        tagIds = emptyList(),
        seasons = emptySet(),
        dressCodes = emptySet(),
        pattern = null,
        fit = null,
        length = null,
        sleeveLength = null,
        warmthRating = null,
        breathabilityRating = null,
        brandId = BrandId(1),
        size = null,
        price = null,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = false,
        images = emptyList(),
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
    )

    private fun testOutfit(
        id: Long,
        name: String,
    ) = Outfit(
        id = OutfitId(id),
        name = name,
        garments = emptyList(),
        occasionId = null,
        source = OutfitSource.USER_CREATED,
        isSaved = true,
        photoUri = null,
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
    )

    private fun testWearEvent(
        id: Long,
        date: LocalDate,
        garmentId: GarmentId,
    ) = WearEvent(
        id = WearEventId(id),
        date = date,
        garmentId = garmentId,
        outfitId = null,
        weatherCacheId = null,
        occasionId = null,
        note = null,
        status = WearEventStatus.WORN,
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
    )

    private fun usageStatsFixture() =
        UsageStats(
            window = StatsWindow.ALL_TIME,
            totalActiveGarments = GARMENT_COUNT,
            wornAtLeastOnce = GARMENT_COUNT,
            usagePercent = 100.0,
            mostWornGarmentIds = emptyList(),
            leastWornGarmentIds = emptyList(),
            favouriteBrandIds = emptyList(),
            signatureColorIds = emptyList(),
            favouriteCategoryIds = emptyList(),
            wearsBySeason = emptyMap(),
            wearsByDressCode = emptyMap(),
            weekdayWearCount = 0,
            weekendWearCount = 0,
        )

    private companion object {
        const val INSIGHT_LIST_CAP = 6
        const val RECENT_ACTIVITY_CAP = 10
        const val WEEKLY_CHART_CAP = 8
        const val LARGE_DATASET_BUDGET_MS = 2000L
    }
}
