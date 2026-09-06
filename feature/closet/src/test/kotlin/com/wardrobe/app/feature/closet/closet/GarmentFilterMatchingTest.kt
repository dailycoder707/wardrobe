package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.FabricComposition
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.MaterialComposition
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The AND/OR contract M17 Part 7B/7M requires:
 * `(Color = Black OR White) AND Season = Winter AND Occasion = Work` — every
 * non-empty facet is OR'd internally, every facet is AND'd against every
 * other. */
class GarmentFilterMatchingTest {
    private val today = LocalDate.of(2026, 8, 8)
    private val topCategory = CategoryId(1)
    private val subCategory = CategoryId(2)
    private val otherTopCategory = CategoryId(3)
    private val categoriesById =
        mapOf(
            topCategory to Category(topCategory, "Tops", null, CategoryLevel.TOP),
            subCategory to Category(subCategory, "T-Shirts", topCategory, CategoryLevel.SUB),
            otherTopCategory to Category(otherTopCategory, "Shoes", null, CategoryLevel.TOP),
        )

    private fun garment(
        id: Long = 1,
        categoryId: CategoryId = topCategory,
        primaryColorId: ColorId? = null,
        secondaryColorId: ColorId? = null,
        materials: List<MaterialId> = emptyList(),
        fabrics: List<FabricId> = emptyList(),
        seasons: Set<Season> = emptySet(),
        dressCodes: Set<DressCode> = emptySet(),
        occasionIds: List<OccasionId> = emptyList(),
        tagIds: List<TagId> = emptyList(),
        fit: Fit? = null,
        gender: GarmentGender? = null,
        waterproofLevel: WaterproofLevel? = null,
        isFavorite: Boolean = false,
        price: Double? = null,
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = categoryId,
        primaryColorId = primaryColorId,
        secondaryColorId = secondaryColorId,
        palette = emptyList(),
        materials = materials.map { MaterialComposition(Material(it, "m"), null) },
        fabrics = fabrics.map { FabricComposition(Fabric(it, "f"), null) },
        tagIds = tagIds,
        seasons = seasons,
        dressCodes = dressCodes,
        occasionIds = occasionIds,
        pattern = null,
        fit = fit,
        length = null,
        sleeveLength = null,
        warmthRating = null,
        breathabilityRating = null,
        brandId = null,
        size = null,
        price = price?.let { Money(it, "GBP") },
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = isFavorite,
        images = emptyList(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        gender = gender,
        waterproofLevel = waterproofLevel,
    )

    private fun Garment.matches(
        filters: ClosetFilterState,
        stats: CostPerWearEntry? = null,
    ) = matchesClosetFilters(filters, categoriesById, stats, today)

    @Test
    fun `empty filters match every garment`() {
        assertTrue(garment().matches(ClosetFilterState.EMPTY))
    }

    @Test
    fun `color filter matches primary or secondary color, OR within the facet`() {
        val black = ColorId(1)
        val white = ColorId(2)
        val red = ColorId(3)
        val filter = ClosetFilterState.EMPTY.copy(colors = setOf(black, white))

        assertTrue(garment(primaryColorId = black).matches(filter))
        assertTrue(garment(secondaryColorId = white).matches(filter))
        assertFalse(garment(primaryColorId = red).matches(filter))
        assertFalse(garment(primaryColorId = null, secondaryColorId = null).matches(filter))
    }

    @Test
    fun `season filter ORs multiple selected seasons`() {
        val filter = ClosetFilterState.EMPTY.copy(seasons = setOf(Season.SUMMER, Season.SPRING))
        assertTrue(garment(seasons = setOf(Season.SUMMER)).matches(filter))
        assertTrue(garment(seasons = setOf(Season.SPRING, Season.WINTER)).matches(filter))
        assertFalse(garment(seasons = setOf(Season.WINTER)).matches(filter))
        assertFalse(garment(seasons = emptySet()).matches(filter))
    }

    @Test
    fun `dress code, occasion, and tag facets each OR within themselves`() {
        val work = OccasionId(1)
        val travel = OccasionId(2)
        val tag = TagId(1)

        assertTrue(
            garment(dressCodes = setOf(DressCode.BUSINESS))
                .matches(ClosetFilterState.EMPTY.copy(dressCodes = setOf(DressCode.BUSINESS, DressCode.CASUAL))),
        )
        assertTrue(
            garment(occasionIds = listOf(work))
                .matches(ClosetFilterState.EMPTY.copy(occasions = setOf(work, travel))),
        )
        assertTrue(garment(tagIds = listOf(tag)).matches(ClosetFilterState.EMPTY.copy(tags = setOf(tag))))
        assertFalse(garment(tagIds = emptyList()).matches(ClosetFilterState.EMPTY.copy(tags = setOf(tag))))
    }

    @Test
    fun `material and fabric facets each OR within themselves`() {
        val cotton = MaterialId(1)
        val wool = MaterialId(2)
        val denim = FabricId(1)

        assertTrue(
            garment(materials = listOf(cotton)).matches(ClosetFilterState.EMPTY.copy(materials = setOf(cotton, wool))),
        )
        assertFalse(garment(materials = emptyList()).matches(ClosetFilterState.EMPTY.copy(materials = setOf(cotton))))
        assertTrue(garment(fabrics = listOf(denim)).matches(ClosetFilterState.EMPTY.copy(fabrics = setOf(denim))))
        assertFalse(garment(fabrics = emptyList()).matches(ClosetFilterState.EMPTY.copy(fabrics = setOf(denim))))
    }

    @Test
    fun `fit, gender, and waterproof level require an exact match and never match a null attribute`() {
        assertTrue(garment(fit = Fit.SLIM).matches(ClosetFilterState.EMPTY.copy(fits = setOf(Fit.SLIM))))
        assertFalse(garment(fit = null).matches(ClosetFilterState.EMPTY.copy(fits = setOf(Fit.SLIM))))
        assertTrue(
            garment(
                gender = GarmentGender.WOMENS,
            ).matches(ClosetFilterState.EMPTY.copy(genders = setOf(GarmentGender.WOMENS))),
        )
        assertTrue(
            garment(waterproofLevel = WaterproofLevel.WATERPROOF)
                .matches(ClosetFilterState.EMPTY.copy(waterproofLevels = setOf(WaterproofLevel.WATERPROOF))),
        )
        assertFalse(
            garment(waterproofLevel = null)
                .matches(ClosetFilterState.EMPTY.copy(waterproofLevels = setOf(WaterproofLevel.NONE))),
        )
    }

    @Test
    fun `top-level category selection also matches its subcategories, but not unrelated categories`() {
        val filter = ClosetFilterState.EMPTY.copy(categories = setOf(topCategory))
        assertTrue(garment(categoryId = topCategory).matches(filter))
        assertTrue(garment(categoryId = subCategory).matches(filter))
        assertFalse(garment(categoryId = otherTopCategory).matches(filter))
    }

    @Test
    fun `combining facets is an AND of each facet's own OR result`() {
        val black = ColorId(1)
        val white = ColorId(2)
        val work = OccasionId(1)
        val filter =
            ClosetFilterState.EMPTY.copy(
                colors = setOf(black, white),
                seasons = setOf(Season.WINTER),
                occasions = setOf(work),
            )

        // Matches every facet.
        assertTrue(
            garment(primaryColorId = black, seasons = setOf(Season.WINTER), occasionIds = listOf(work)).matches(filter),
        )
        // Matches color + season but not occasion.
        assertFalse(
            garment(primaryColorId = white, seasons = setOf(Season.WINTER), occasionIds = emptyList()).matches(filter),
        )
        // Matches color + occasion but not season.
        assertFalse(
            garment(primaryColorId = black, seasons = setOf(Season.SUMMER), occasionIds = listOf(work)).matches(filter),
        )
        // Matches season + occasion but not color.
        assertFalse(
            garment(
                primaryColorId = ColorId(9),
                seasons = setOf(Season.WINTER),
                occasionIds = listOf(work),
            ).matches(filter),
        )
    }

    @Test
    fun `favorite only excludes non-favorited garments`() {
        val filter = ClosetFilterState.EMPTY.copy(favoriteOnly = true)
        assertTrue(garment(isFavorite = true).matches(filter))
        assertFalse(garment(isFavorite = false).matches(filter))
    }

    @Test
    fun `price range excludes garments outside it, including garments with no price`() {
        val filter = ClosetFilterState.EMPTY.copy(priceMin = 20.0, priceMax = 50.0)
        assertTrue(garment(price = 30.0).matches(filter))
        assertFalse(garment(price = 10.0).matches(filter))
        assertFalse(garment(price = 60.0).matches(filter))
        assertFalse(garment(price = null).matches(filter))
    }

    @Test
    fun `never worn requires a zero wear count from real stats`() {
        val filter = ClosetFilterState.EMPTY.copy(neverWorn = true)
        assertTrue(garment().matches(filter, stats = CostPerWearEntry(GarmentId(1), 0, null, null)))
        assertFalse(garment().matches(filter, stats = CostPerWearEntry(GarmentId(1), 2, null, null)))
        assertTrue(garment().matches(filter, stats = null))
    }

    @Test
    fun `recently worn requires a real last-worn date within the window`() {
        val filter = ClosetFilterState.EMPTY.copy(recentlyWornOnly = true)
        val recent = CostPerWearEntry(GarmentId(1), 1, null, today.minusDays(5))
        val old = CostPerWearEntry(GarmentId(1), 1, null, today.minusDays(60))
        assertTrue(garment().matches(filter, stats = recent))
        assertFalse(garment().matches(filter, stats = old))
        assertFalse(garment().matches(filter, stats = null))
    }
}
