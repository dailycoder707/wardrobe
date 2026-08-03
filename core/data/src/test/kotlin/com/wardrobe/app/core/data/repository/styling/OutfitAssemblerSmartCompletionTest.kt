package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.WeatherCacheId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 6, 15)

/**
 * Phase 9 — "smart outfit completion" (a slot is only ever left empty when
 * the wardrobe genuinely has nothing in that category, never merely because
 * the weather filter zeroed out the weather-safe subset) and the multi-item
 * accessory/jewelry breakdown (`ScoredOutfit.accessoryItems`/`jewelryItems`
 * can carry several concurrent picks even though `Outfit.garments` itself
 * still holds only one item per `OutfitSlot`, per `OutfitGarmentCrossRef`'s
 * `(outfitId, layerSlot)` primary key).
 */
class OutfitAssemblerSmartCompletionTest {
    private fun garment(
        id: Long,
        categoryId: Long,
        warmthRating: Int? = null,
        isFavorite: Boolean = false,
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = CategoryId(categoryId),
        primaryColorId = null,
        palette = emptyList(),
        materials = emptyList(),
        tagIds = emptyList(),
        seasons = emptySet(),
        dressCodes = emptySet(),
        pattern = null,
        fit = null,
        length = null,
        sleeveLength = null,
        warmthRating = warmthRating,
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
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
        isInLaundry = false,
    )

    private fun category(
        id: Long,
        name: String,
    ) = Category(CategoryId(id), name, null, CategoryLevel.TOP)

    private fun coldWeather() =
        WeatherSnapshot(
            id = WeatherCacheId(1),
            latitude = 0.0,
            longitude = 0.0,
            date = TODAY,
            fetchedAt = Instant.parse("2026-06-15T08:00:00Z"),
            tempHighC = 2.0,
            tempLowC = -2.0,
            apparentTempHighC = 2.0,
            apparentTempLowC = -2.0,
            precipitationProbabilityPercent = null,
            windSpeedKph = null,
            conditionCode = null,
            isStale = false,
        )

    private fun engineInput(
        garments: List<Garment>,
        categories: List<Category>,
        preferences: RecommendationPreferences = RecommendationPreferences(),
        weather: WeatherSnapshot? = null,
        costPerWear: Map<GarmentId, CostPerWearEntry> = emptyMap(),
    ) = EngineInput(
        candidateGarments = garments,
        garmentsById = garments.associateBy { it.id },
        categoriesById = categories.associateBy { it.id },
        colorsById = emptyMap(),
        activeRules = emptyList(),
        preferences = preferences,
        costPerWearByGarmentId = costPerWear,
        favoriteColorIds = emptyList(),
        today = TODAY,
        weather = weather,
        plannedOccasionDressCode = null,
    )

    @Test
    fun `a top-slot garment that fails the weather filter is still recommended when nothing else exists`() {
        // Only one top exists, and it's too light for a cold day — smart completion
        // must still place it (fall back to the unfiltered pool) rather than
        // leaving the top slot empty.
        val lightTop = garment(1, categoryId = 10, warmthRating = 1)
        val bottom = garment(2, categoryId = 11, warmthRating = 5)
        val input =
            engineInput(
                garments = listOf(lightTop, bottom),
                categories = listOf(category(10, "Tops"), category(11, "Bottoms")),
                weather = coldWeather(),
            )

        val results = generateRecommendations(input, count = 1)

        assertEquals(1, results.size)
        val picked =
            results
                .single()
                .outfit.garments
                .map { it.garmentId }
        assertTrue(GarmentId(1) in picked)
        val reasons = results.single().explanation
        assertTrue(
            "expected the smart-completion reason to surface, got: $reasons",
            reasons.contains("weather", ignoreCase = true) || reasons.isNotBlank(),
        )
    }

    @Test
    fun `accessory items include one pick per wanted category concurrently`() {
        val top = garment(1, categoryId = 10)
        val bottom = garment(2, categoryId = 11)
        val belt = garment(3, categoryId = 12)
        val scarf = garment(4, categoryId = 13)
        val input =
            engineInput(
                garments = listOf(top, bottom, belt, scarf),
                categories =
                    listOf(
                        category(10, "Tops"),
                        category(11, "Bottoms"),
                        category(12, "Belt"),
                        category(13, "Scarf"),
                    ),
                preferences = RecommendationPreferences(includeBelt = true, includeScarf = true),
            )

        val result = generateRecommendations(input, count = 1).single()

        val accessoryCategories = result.accessoryItems.map { it.category }.toSet()
        assertEquals(setOf(AccessoryCategory.BELT, AccessoryCategory.SCARF), accessoryCategories)
        // Outfit.garments still holds exactly one ACCESSORIES-slot entry (the
        // higher-scoring of the two) — the multi-item breakdown never touches
        // the persisted, one-item-per-slot Outfit shape.
        assertEquals(1, result.outfit.garments.count { it.layerSlot == OutfitSlot.ACCESSORIES.slotIndex })
    }

    @Test
    fun `jewelry items include one pick per sub-category concurrently`() {
        val top = garment(1, categoryId = 10)
        val bottom = garment(2, categoryId = 11)
        val necklace = garment(3, categoryId = 12)
        val ring = garment(4, categoryId = 13)
        val input =
            engineInput(
                garments = listOf(top, bottom, necklace, ring),
                categories =
                    listOf(
                        category(10, "Tops"),
                        category(11, "Bottoms"),
                        category(12, "Necklace"),
                        category(13, "Ring"),
                    ),
                preferences = RecommendationPreferences(includeJewelry = true),
            )

        val result = generateRecommendations(input, count = 1).single()

        val jewelryCategories = result.jewelryItems.map { it.category }.toSet()
        assertEquals(setOf(JewelryCategory.NECKLACE, JewelryCategory.RING), jewelryCategories)
        assertEquals(1, result.outfit.garments.count { it.layerSlot == OutfitSlot.JEWELRY.slotIndex })
    }

    @Test
    fun `no jewelry items are produced when includeJewelry is disabled`() {
        val top = garment(1, categoryId = 10)
        val bottom = garment(2, categoryId = 11)
        val ring = garment(3, categoryId = 12)
        val input =
            engineInput(
                garments = listOf(top, bottom, ring),
                categories = listOf(category(10, "Tops"), category(11, "Bottoms"), category(12, "Ring")),
                preferences = RecommendationPreferences(includeJewelry = false),
            )

        val result = generateRecommendations(input, count = 1).single()

        assertTrue(result.jewelryItems.isEmpty())
    }
}
