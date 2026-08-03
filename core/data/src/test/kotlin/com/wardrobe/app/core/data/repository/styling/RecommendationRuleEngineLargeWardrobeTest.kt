package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private const val GARMENT_COUNT = 1000
private const val SUGGESTION_COUNT = 3
private const val BUDGET_MS = 2000L

/** "Support 1000 garments... no noticeable delay" (this phase's own brief) —
 * a plain JVM test (no Room/Robolectric needed) targeting the pure Kotlin
 * scoring/assembly functions this module owns, the same large-dataset
 * discipline Phase 5e's `InsightsBuildersLargeDatasetTest` established. */
class RecommendationRuleEngineLargeWardrobeTest {
    @Test
    fun `generating recommendations from 1000 garments stays bounded and fast`() {
        val categories =
            listOf(
                Category(CategoryId(1), "Tops", null, CategoryLevel.TOP),
                Category(CategoryId(2), "Bottoms", null, CategoryLevel.TOP),
                Category(CategoryId(3), "Shoes", null, CategoryLevel.TOP),
                Category(CategoryId(4), "Bags", null, CategoryLevel.TOP),
            )
        val garments =
            (1..GARMENT_COUNT).map { index ->
                testGarment(index.toLong(), categoryId = ((index % 4) + 1).toLong())
            }
        val input =
            EngineInput(
                candidateGarments = garments,
                garmentsById = garments.associateBy { it.id },
                categoriesById = categories.associateBy { it.id },
                colorsById = emptyMap(),
                activeRules = emptyList(),
                preferences = RecommendationPreferences(),
                costPerWearByGarmentId = emptyMap(),
                favoriteColorIds = emptyList(),
                today = LocalDate.of(2026, 6, 15),
                weather = null,
            )

        val start = System.nanoTime()
        val results = generateRecommendations(input, SUGGESTION_COUNT)
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertTrue("expected at least one complete outfit", results.isNotEmpty())
        assertTrue(results.size <= SUGGESTION_COUNT)
        assertTrue(
            "expected under ${BUDGET_MS}ms for $GARMENT_COUNT garments, took ${elapsedMillis}ms",
            elapsedMillis < BUDGET_MS,
        )
    }

    private fun testGarment(
        id: Long,
        categoryId: Long,
    ) = Garment(
        id = GarmentId(id),
        name = "Garment $id",
        categoryId = CategoryId(categoryId),
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
        isFavorite = id % 10 == 0L,
        images = emptyList(),
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
    )
}
