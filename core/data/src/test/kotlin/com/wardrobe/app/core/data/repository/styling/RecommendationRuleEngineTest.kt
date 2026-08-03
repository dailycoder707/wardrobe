package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.StyleRuleId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.StyleRule
import com.wardrobe.app.core.model.styling.StyleRuleSourceType
import com.wardrobe.app.core.model.styling.StyleRuleType
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 6, 15)

class RecommendationRuleEngineTest {
    private fun garment(
        id: Long,
        categoryId: Long,
        isFavorite: Boolean = false,
        isInLaundry: Boolean = false,
        status: GarmentStatus = GarmentStatus.ACTIVE,
        primaryColorId: Long? = null,
        brandId: Long? = null,
        warmthRating: Int? = null,
        dressCodes: Set<DressCode> = emptySet(),
        fit: com.wardrobe.app.core.model.garment.Fit? = null,
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = CategoryId(categoryId),
        primaryColorId = primaryColorId?.let(::ColorId),
        palette = emptyList(),
        materials = emptyList(),
        tagIds = emptyList(),
        seasons = emptySet(),
        dressCodes = dressCodes,
        pattern = null,
        fit = fit,
        length = null,
        sleeveLength = null,
        warmthRating = warmthRating,
        breathabilityRating = null,
        brandId = brandId?.let(::BrandId),
        size = null,
        price = null,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = status,
        isReviewed = true,
        isFavorite = isFavorite,
        images = emptyList(),
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
        isInLaundry = isInLaundry,
    )

    private fun category(
        id: Long,
        name: String,
    ) = Category(CategoryId(id), name, null, CategoryLevel.TOP)

    private fun engineInput(
        garments: List<Garment>,
        categories: List<Category>,
        colors: List<Color> = emptyList(),
        rules: List<StyleRule> = emptyList(),
        preferences: RecommendationPreferences = RecommendationPreferences(),
        costPerWear: Map<GarmentId, CostPerWearEntry> = emptyMap(),
        favoriteColorIds: List<ColorId> = emptyList(),
        weather: WeatherSnapshot? = null,
        plannedOccasionDressCode: DressCode? = null,
    ) = EngineInput(
        candidateGarments = garments,
        garmentsById = garments.associateBy { it.id },
        categoriesById = categories.associateBy { it.id },
        colorsById = colors.associateBy { it.id },
        activeRules = rules,
        preferences = preferences,
        costPerWearByGarmentId = costPerWear,
        favoriteColorIds = favoriteColorIds,
        today = TODAY,
        weather = weather,
        plannedOccasionDressCode = plannedOccasionDressCode,
    )

    private fun weatherSnapshot(
        apparentTempHighC: Double? = null,
        condition: com.wardrobe.app.core.model.weather.WeatherCondition? = null,
    ) = WeatherSnapshot(
        id =
            com.wardrobe.app.core.model.common
                .WeatherCacheId(1),
        latitude = 0.0,
        longitude = 0.0,
        date = TODAY,
        fetchedAt = Instant.parse("2026-06-15T08:00:00Z"),
        tempHighC = apparentTempHighC,
        tempLowC = apparentTempHighC,
        apparentTempHighC = apparentTempHighC,
        apparentTempLowC = apparentTempHighC,
        precipitationProbabilityPercent = null,
        windSpeedKph = null,
        conditionCode = null,
        isStale = false,
        condition = condition,
    )

    @Test
    fun `buildSlotCandidates classifies garments by category name`() {
        val input =
            engineInput(
                garments = listOf(garment(1, categoryId = 10), garment(2, categoryId = 11)),
                categories = listOf(category(10, "Tops"), category(11, "Shoes")),
            )

        val bySlot = buildSlotCandidates(input)

        assertEquals(listOf(GarmentId(1)), bySlot[OutfitSlot.TOP]?.map { it.id })
        assertEquals(listOf(GarmentId(2)), bySlot[OutfitSlot.SHOES]?.map { it.id })
    }

    @Test
    fun `buildSlotCandidates excludes garments an active AVOID_CATEGORY rule rejects`() {
        val rule =
            StyleRule(
                id = StyleRuleId(1),
                description = "avoid formal shoes",
                sourceType = StyleRuleSourceType.USER_AUTHORED,
                sourceFeedbackId = null,
                ruleType = StyleRuleType.AVOID_CATEGORY,
                parametersJson = "categoryId=11",
                isActive = true,
                createdAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val input =
            engineInput(
                garments = listOf(garment(1, categoryId = 11)),
                categories = listOf(category(11, "Shoes")),
                rules = listOf(rule),
            )

        assertTrue(buildSlotCandidates(input)[OutfitSlot.SHOES].orEmpty().isEmpty())
    }

    @Test
    fun `scoreCandidate rewards favorites when preferFavorites is enabled`() {
        val fav = garment(1, categoryId = 10, isFavorite = true)
        val nonFav = garment(2, categoryId = 10, isFavorite = false)
        val input =
            engineInput(
                garments = listOf(fav, nonFav),
                categories = listOf(category(10, "Tops")),
                preferences = RecommendationPreferences(preferFavorites = true),
            )

        val favScore = scoreCandidate(fav, input)
        val nonFavScore = scoreCandidate(nonFav, input)

        assertTrue(favScore.score > nonFavScore.score)
        assertTrue(favScore.reasons.any { it.contains("favorite") })
    }

    @Test
    fun `scoreCandidate penalizes a garment worn within the repeat interval`() {
        val recent = garment(1, categoryId = 10)
        val input =
            engineInput(
                garments = listOf(recent),
                categories = listOf(category(10, "Tops")),
                preferences = RecommendationPreferences(avoidRecentlyWorn = true, maxRepeatIntervalDays = 7),
                costPerWear =
                    mapOf(
                        GarmentId(1) to
                            CostPerWearEntry(
                                GarmentId(1),
                                totalWearCount = 5,
                                costPerWear = null,
                                lastWornDate = TODAY.minusDays(2),
                            ),
                    ),
            )

        val neverWorn = garment(2, categoryId = 10)
        val neverWornInput =
            input.copy(
                candidateGarments = listOf(neverWorn),
                garmentsById =
                    mapOf(GarmentId(2) to neverWorn),
            )

        assertTrue(scoreCandidate(recent, input).score < scoreCandidate(neverWorn, neverWornInput).score)
    }

    @Test
    fun `passesWeatherFilter always passes when weather is null`() {
        val garment = garment(1, categoryId = 10, warmthRating = 1)
        assertTrue(passesWeatherFilter(garment, null))
    }

    @Test
    fun `passesWeatherFilter rejects a low-warmth garment in cold apparent temperature`() {
        val lightGarment = garment(1, categoryId = 10, warmthRating = 1)
        val weather =
            WeatherSnapshot(
                id =
                    com.wardrobe.app.core.model.common
                        .WeatherCacheId(1),
                latitude = 0.0,
                longitude = 0.0,
                date = TODAY,
                fetchedAt = Instant.now(),
                tempHighC = 5.0,
                tempLowC = 0.0,
                apparentTempHighC = 2.0,
                apparentTempLowC = -2.0,
                precipitationProbabilityPercent = null,
                windSpeedKph = null,
                conditionCode = null,
                isStale = false,
            )

        assertFalse(passesWeatherFilter(lightGarment, weather))
    }

    @Test
    fun `generateRecommendations builds a complete top plus bottom outfit`() {
        val input =
            engineInput(
                garments =
                    listOf(
                        garment(1, categoryId = 10),
                        garment(2, categoryId = 11),
                    ),
                categories = listOf(category(10, "Tops"), category(11, "Bottoms")),
            )

        val results = generateRecommendations(input, count = 1)

        assertEquals(1, results.size)
        assertEquals(
            2,
            results
                .single()
                .outfit.garments.size,
        )
    }

    @Test
    fun `generateRecommendations returns nothing when neither a dress nor a top-and-bottom pair exists`() {
        val input =
            engineInput(
                garments = listOf(garment(1, categoryId = 12)),
                categories = listOf(category(12, "Bags")),
            )

        assertTrue(generateRecommendations(input, count = 1).isEmpty())
    }

    @Test
    fun `generateRecommendations enforces an ALWAYS_INCLUDE_CATEGORY rule`() {
        val rule =
            StyleRule(
                id = StyleRuleId(1),
                description = "always include a scarf",
                sourceType = StyleRuleSourceType.USER_AUTHORED,
                sourceFeedbackId = null,
                ruleType = StyleRuleType.ALWAYS_INCLUDE_CATEGORY,
                parametersJson = "categoryId=99",
                isActive = true,
                createdAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val input =
            engineInput(
                garments = listOf(garment(1, categoryId = 10), garment(2, categoryId = 11)),
                categories = listOf(category(10, "Tops"), category(11, "Bottoms")),
                rules = listOf(rule),
            )

        assertTrue(generateRecommendations(input, count = 1).isEmpty())
    }

    @Test
    fun `suggestReplacementForSlot-style candidate lookup excludes the current garment`() {
        val input =
            engineInput(
                garments = listOf(garment(1, categoryId = 10), garment(2, categoryId = 10, isFavorite = true)),
                categories = listOf(category(10, "Tops")),
            )

        val candidates = buildSlotCandidates(input)[OutfitSlot.TOP].orEmpty().filter { it.id != GarmentId(1) }

        assertEquals(listOf(GarmentId(2)), candidates.map { it.id })
    }

    @Test
    fun `buildExplanation falls back to a generic sentence with no reasons`() {
        assertEquals(
            "A complete outfit built from what's currently available.",
            buildExplanation(emptyMap(), harmony = null),
        )
    }

    @Test
    fun `buildExplanation never mentions MIXED color harmony`() {
        val sentence = buildExplanation(emptyMap(), harmony = com.wardrobe.app.core.domain.styling.ColorHarmony.MIXED)
        assertFalse(sentence.contains("mixed", ignoreCase = true))
    }

    @Test
    fun `decodeRuleParameters round-trips encodeRuleParameters`() {
        val params = mapOf("categoryId" to "42", "brandId" to "7")
        assertEquals(params, decodeRuleParameters(encodeRuleParameters(params)))
    }

    @Test
    fun `decodeRuleParameters tolerates a blank string`() {
        assertTrue(decodeRuleParameters("").isEmpty())
    }

    @Test
    fun `scoreCandidate rewards a warm outerwear layer on a cold day`() {
        val warmJacket = garment(1, categoryId = 10, warmthRating = 5)
        val input =
            engineInput(
                garments = listOf(warmJacket),
                categories = listOf(category(10, "Outerwear")),
                weather = weatherSnapshot(apparentTempHighC = 5.0),
            )

        val result = scoreCandidate(warmJacket, input)

        assertTrue(result.reasons.any { it.contains("cool", ignoreCase = true) })
    }

    @Test
    fun `scoreCandidate rewards outerwear on a rainy day`() {
        val jacket = garment(1, categoryId = 10, warmthRating = 1)
        val input =
            engineInput(
                garments = listOf(jacket),
                categories = listOf(category(10, "Outerwear")),
                weather =
                    weatherSnapshot(
                        apparentTempHighC = 20.0,
                        condition = com.wardrobe.app.core.model.weather.WeatherCondition.RAIN,
                    ),
            )

        val result = scoreCandidate(jacket, input)

        assertTrue(result.reasons.any { it.contains("rain", ignoreCase = true) })
    }

    @Test
    fun `scoreCandidate does not apply a weather bonus when weather is null`() {
        val jacket = garment(1, categoryId = 10, warmthRating = 5)
        val input =
            engineInput(garments = listOf(jacket), categories = listOf(category(10, "Outerwear")), weather = null)

        val result = scoreCandidate(jacket, input)

        assertFalse(
            result.reasons.any {
                it.contains("cool", ignoreCase = true) ||
                    it.contains("rain", ignoreCase = true)
            },
        )
    }

    @Test
    fun `scoreCandidate rewards a garment matching today's planned occasion dress code`() {
        val formalTop = garment(1, categoryId = 10, dressCodes = setOf(DressCode.FORMAL))
        val casualTop = garment(2, categoryId = 10, dressCodes = setOf(DressCode.CASUAL))
        val input =
            engineInput(
                garments = listOf(formalTop, casualTop),
                categories = listOf(category(10, "Tops")),
                plannedOccasionDressCode = DressCode.FORMAL,
            )

        val formalScore = scoreCandidate(formalTop, input)
        val casualScore = scoreCandidate(casualTop, input)

        assertTrue(formalScore.score > casualScore.score)
        assertTrue(formalScore.reasons.any { it.contains("plans", ignoreCase = true) })
    }
}
