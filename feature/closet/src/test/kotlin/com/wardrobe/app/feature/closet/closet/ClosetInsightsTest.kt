package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ClosetInsightsTest {
    private fun garment(
        id: Long,
        isFavorite: Boolean = false,
        seasons: Set<Season> = emptySet(),
        dressCodes: Set<DressCode> = emptySet(),
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = CategoryId(1),
        primaryColorId = null,
        palette = emptyList(),
        materials = emptyList(),
        tagIds = emptyList(),
        seasons = seasons,
        dressCodes = dressCodes,
        pattern = null,
        fit = null,
        length = null,
        sleeveLength = null,
        warmthRating = null,
        breathabilityRating = null,
        brandId = null,
        size = null,
        price = null,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = isFavorite,
        images = emptyList(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `currentSeason maps every month to a real meteorological season`() {
        assertEquals(Season.WINTER, currentSeason(LocalDate.of(2026, 1, 15)))
        assertEquals(Season.SPRING, currentSeason(LocalDate.of(2026, 4, 15)))
        assertEquals(Season.SUMMER, currentSeason(LocalDate.of(2026, 8, 8)))
        assertEquals(Season.AUTUMN, currentSeason(LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `insight counts are computed from the real garment list, never hardcoded`() {
        val today = LocalDate.of(2026, 8, 8)
        val garments =
            listOf(
                garment(1, isFavorite = true, seasons = setOf(Season.SUMMER)),
                garment(2, isFavorite = true),
                garment(3, dressCodes = setOf(DressCode.BUSINESS)),
            )

        val insights = computeClosetInsights(garments, today)

        assertTrue(insights.any { it.label == "2 favorites" && it.count == 2 })
        assertTrue(insights.any { it.label == "1 summer item" && it.count == 1 })
        assertTrue(insights.any { it.label == "1 work-ready item" && it.count == 1 })
    }

    @Test
    fun `a zero count insight is omitted rather than shown as 0 items`() {
        val insights = computeClosetInsights(emptyList(), LocalDate.of(2026, 8, 8))
        assertEquals(emptyList<ClosetInsight>(), insights)
    }

    @Test
    fun `tapping an insight applies the exact filter its count describes`() {
        val today = LocalDate.of(2026, 8, 8)
        val garments = listOf(garment(1, isFavorite = true))

        val favoritesInsight = computeClosetInsights(garments, today).first { it.label == "1 favorite" }

        assertEquals(ClosetFilterState.EMPTY.copy(favoriteOnly = true), favoritesInsight.filterToApply)
    }
}
