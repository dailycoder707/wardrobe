package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.WearEventEntity
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.intelligence.ForgottenBucket
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val TODAY_INSTANT = Instant.parse("2026-06-15T00:00:00Z")

/**
 * Phase 9 — the two riskiest pieces of new derived logic: the rotation-score
 * formula (built from real `wear_events`/`outfit_garments` rows via the same
 * `all_wears` dual-source CTE every other stat already uses) and duplicate
 * detection's category+color grouping. Domain repositories this class also
 * depends on but that these specific scenarios don't exercise are mocked
 * with MockK, the same mixed real-DB-plus-mocks approach
 * `StylingEngineRepositoryImplTest` already established.
 */
@RunWith(RobolectricTestRunner::class)
class WardrobeIntelligenceRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: WardrobeIntelligenceRepositoryImpl
    private val fixedClock: Clock = Clock.fixed(TODAY_INSTANT, ZoneOffset.UTC)

    private var categoryId = 0L
    private var colorId = 0L

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCategory(name: String = "Tops"): Long =
        db.categoryDao().insert(
            CategoryEntity(
                name = name,
                parentId = null,
                level = CategoryLevel.TOP,
                syncId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
            ),
        )

    private suspend fun insertGarment(
        name: String,
        primaryColorId: Long? = null,
        brandId: Long? = null,
    ): Long {
        if (categoryId == 0L) categoryId = insertCategory()
        return db.garmentDao().insert(
            GarmentEntity(
                name = name,
                categoryId = categoryId,
                primaryColorId = primaryColorId,
                pattern = null,
                fit = null,
                length = null,
                sleeveLength = null,
                warmthRating = null,
                breathabilityRating = null,
                brandId = brandId,
                size = null,
                price = null,
                currencyCode = null,
                purchaseDate = null,
                condition = null,
                careNotes = null,
                status = GarmentStatus.ACTIVE,
                isReviewed = true,
                searchText = name.lowercase(),
                createdAt = 0L,
                updatedAt = 0L,
                syncId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
            ),
        )
    }

    private suspend fun insertColor(name: String = "Black"): Long =
        db.colorDao().insert(
            com.wardrobe.app.core.database.entity.ColorEntity(
                name = name,
                hexValue = "#000000",
                syncId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
            ),
        )

    private suspend fun logWear(
        garmentId: Long,
        date: String,
    ) {
        db.wearEventDao().insert(
            WearEventEntity(
                date = date,
                garmentId = garmentId,
                outfitId = null,
                weatherCacheId = null,
                occasionId = null,
                note = null,
                status = com.wardrobe.app.core.model.wear.WearEventStatus.WORN,
                createdAt = 0L,
                syncId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
            ),
        )
    }

    private fun buildRepository(garmentRepository: GarmentRepository): WardrobeIntelligenceRepositoryImpl =
        WardrobeIntelligenceRepositoryImpl(
            daos = WardrobeIntelligenceDaos(db.statsDao(), db.feedbackDao()),
            repositories =
                WardrobeIntelligenceRepositories(
                    garmentRepository = garmentRepository,
                    outfitRepository = mockk<OutfitRepository>(relaxed = true),
                    categoryRepository = mockk<CategoryRepository>(relaxed = true),
                    occasionRepository =
                        mockk<OccasionRepository> {
                            every { observeAll() } returns
                                flowOf(
                                    emptyList(),
                                )
                        },
                    statsRepository = mockk<StatsRepository>(relaxed = true),
                    wearEventRepository = mockk<WearEventRepository>(relaxed = true),
                    tripRepository = mockk<TripRepository> { every { observeTrips() } returns flowOf(emptyList()) },
                    stylingEngineRepository = mockk<StylingEngineRepository>(relaxed = true),
                    weatherRepository = mockk<WeatherRepository>(relaxed = true),
                ),
            clock = fixedClock,
        )

    @Test
    fun `observeForgottenGarments buckets by the largest threshold crossed`() =
        runTest {
            val recentId = insertGarment("Recent")
            logWear(recentId, "2026-06-10") // 5 days ago — not forgotten

            val ninetyId = insertGarment("Old")
            logWear(ninetyId, "2026-03-01") // ~106 days ago — NINETY bucket

            // no wear events at all — NOT forgotten (never-worn is a separate bucket)
            val neverWornId = insertGarment("NeverWorn")

            val garmentRepository =
                mockk<GarmentRepository> {
                    every { observeGarments(any()) } returns flowOf(emptyList())
                }
            repository = buildRepository(garmentRepository)
            val forgotten = repository.observeWardrobeAlerts().first().forgotten

            assertTrue(forgotten.none { it.garmentId.value == recentId })
            assertTrue(forgotten.none { it.garmentId.value == neverWornId })
            val oldEntry = forgotten.first { it.garmentId.value == ninetyId }
            assertEquals(ForgottenBucket.NINETY, oldEntry.bucket)
        }

    @Test
    fun `observeGarmentInsights computes rotation score from real wear history`() =
        runTest {
            val garmentId = insertGarment("Jacket")
            // Worn every 10 days, three times — average interval 10 days.
            logWear(garmentId, "2026-05-16")
            logWear(garmentId, "2026-05-26")
            logWear(garmentId, "2026-06-05")
            // 10 days since last wear on the fixed "today" (2026-06-15) — exactly
            // on schedule relative to its own 10-day average, so rotationScore == 50.

            val garment =
                Garment(
                    id = GarmentId(garmentId),
                    name = "Jacket",
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
                    warmthRating = null,
                    breathabilityRating = null,
                    brandId = null,
                    size = null,
                    price = Money(100.0, "GBP"),
                    purchaseDate = null,
                    condition = null,
                    careNotes = null,
                    status = GarmentStatus.ACTIVE,
                    isReviewed = true,
                    isFavorite = false,
                    images = emptyList(),
                    createdAt = Instant.parse("2020-01-01T00:00:00Z"),
                    updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
                    isInLaundry = false,
                )
            val garmentRepository =
                mockk<GarmentRepository> {
                    every { observeGarment(GarmentId(garmentId)) } returns flowOf(garment)
                }
            repository = buildRepository(garmentRepository)

            val insights = repository.observeGarmentInsights(GarmentId(garmentId)).first()

            requireNotNull(insights)
            assertEquals(3, insights.totalWears)
            assertEquals(50, insights.rotationScore)
        }

    @Test
    fun `observeDuplicateGroups groups active garments sharing category and color`() =
        runTest {
            val color = insertColor()
            val firstId = insertGarment("Blue Shirt A", primaryColorId = color)
            val secondId = insertGarment("Blue Shirt B", primaryColorId = color)
            insertGarment("Solo Item", primaryColorId = null)

            val garmentRepository =
                mockk<GarmentRepository> {
                    every { observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)) } returns
                        flowOf(
                            listOf(
                                minimalGarment(firstId, categoryId, color),
                                minimalGarment(secondId, categoryId, color),
                            ),
                        )
                }
            repository = buildRepository(garmentRepository)

            val groups = repository.observeDuplicateGroups().first()

            assertEquals(1, groups.size)
            assertEquals(setOf(GarmentId(firstId), GarmentId(secondId)), groups.single().garmentIds.toSet())
            assertTrue(groups.single().matchedOnCategory)
            assertTrue(groups.single().matchedOnColor)
        }

    private fun minimalGarment(
        id: Long,
        categoryId: Long,
        colorId: Long?,
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = CategoryId(categoryId),
        primaryColorId = colorId?.let(::ColorId),
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
        brandId = null,
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
        isInLaundry = false,
    )
}
