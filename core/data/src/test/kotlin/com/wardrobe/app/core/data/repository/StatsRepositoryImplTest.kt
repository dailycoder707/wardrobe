package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.WearEventEntity
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Targets phase-3-persistence.md's own flagged risk: "the union-of-two-sources
 * wear-count query... is easy to get subtly wrong (e.g. double-counting a garment
 * worn twice in the same outfit on the same day)." A garment worn only directly,
 * only via an outfit, via both, and never at all must each count correctly.
 */
@RunWith(RobolectricTestRunner::class)
class StatsRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: StatsRepositoryImpl
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)

    private var categoryId = 0L

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        repository = StatsRepositoryImpl(db.statsDao(), fixedClock)
    }

    private suspend fun insertGarment(
        name: String,
        price: Double? = 100.0,
    ): Long {
        if (categoryId == 0L) {
            categoryId =
                db.categoryDao().insert(
                    CategoryEntity(
                        name = "Tops",
                        parentId = null,
                        level = CategoryLevel.TOP,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                    ),
                )
        }
        return db.garmentDao().insert(
            GarmentEntity(
                name = name,
                categoryId = categoryId,
                primaryColorId = null,
                pattern = null,
                fit = null,
                length = null,
                sleeveLength = null,
                warmthRating = null,
                breathabilityRating = null,
                brandId = null,
                size = null,
                price = price,
                currencyCode = if (price != null) "GBP" else null,
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

    @Test
    fun `wear count includes both direct wear events and wears via an outfit`() =
        runTest {
            val directOnly = insertGarment("Direct Only")
            val viaOutfitOnly = insertGarment("Via Outfit Only")
            val both = insertGarment("Both")
            val neverWorn = insertGarment("Never Worn")

            // directOnly: worn twice, logged directly.
            db.wearEventDao().insert(wearEvent(date = "2026-06-01", garmentId = directOnly))
            db.wearEventDao().insert(wearEvent(date = "2026-06-02", garmentId = directOnly))

            // viaOutfitOnly + both: both appear in one outfit, worn twice.
            val outfitId =
                db.outfitDao().insert(
                    OutfitEntity(
                        name = "Look",
                        occasionId = null,
                        source = OutfitSource.USER_CREATED,
                        isSaved = true,
                        photoUri = null,
                        createdAt = 0L,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                    ),
                )
            db.outfitDao().insertGarmentSlots(
                listOf(
                    OutfitGarmentCrossRef(outfitId, layerSlot = 0, garmentId = viaOutfitOnly),
                    OutfitGarmentCrossRef(outfitId, layerSlot = 1, garmentId = both),
                ),
            )
            db.wearEventDao().insert(wearEvent(date = "2026-06-03", outfitId = outfitId))
            db.wearEventDao().insert(wearEvent(date = "2026-06-04", outfitId = outfitId))

            // both: also worn directly once more.
            db.wearEventDao().insert(wearEvent(date = "2026-06-05", garmentId = both))

            val rows = repository.observeCostPerWear().first().associateBy { it.garmentId.value }

            assertEquals(2, rows.getValue(directOnly).totalWearCount)
            assertEquals(2, rows.getValue(viaOutfitOnly).totalWearCount)
            assertEquals(3, rows.getValue(both).totalWearCount)
            assertEquals(0, rows.getValue(neverWorn).totalWearCount)
        }

    @Test
    fun `dormant items are those with zero wears since the window cutoff`() =
        runTest {
            val wornRecently = insertGarment("Worn Recently")
            val wornLongAgo = insertGarment("Worn Long Ago")

            db.wearEventDao().insert(wearEvent(date = "2026-06-10", garmentId = wornRecently))
            db.wearEventDao().insert(wearEvent(date = "2024-01-01", garmentId = wornLongAgo))

            val dormant = repository.observeDormantItems(StatsWindow.ONE_MONTH).first().map { it.garmentId.value }

            assertEquals(listOf(wornLongAgo), dormant)
        }

    private fun wearEvent(
        date: String,
        garmentId: Long? = null,
        outfitId: Long? = null,
        status: WearEventStatus = WearEventStatus.WORN,
    ) = WearEventEntity(
        date = date,
        garmentId = garmentId,
        outfitId = outfitId,
        weatherCacheId = null,
        occasionId = null,
        note = null,
        status = status,
        createdAt = 0L,
        syncId =
            java.util.UUID
                .randomUUID()
                .toString(),
    )

    private suspend fun insertOutfit(
        name: String,
        isSaved: Boolean = true,
        isArchived: Boolean = false,
    ): Long =
        db.outfitDao().insert(
            OutfitEntity(
                name = name,
                occasionId = null,
                source = OutfitSource.USER_CREATED,
                isSaved = isSaved,
                isArchived = isArchived,
                photoUri = null,
                createdAt = 0L,
                syncId =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
            ),
        )

    @Test
    fun `favourite categories are ordered by wear count and joined to the garment's own category`() =
        runTest {
            val topGarment = insertGarment("Shirt")
            val topsCategory = categoryId
            val bottomsCategory =
                db.categoryDao().insert(
                    CategoryEntity(
                        name = "Bottoms",
                        parentId = null,
                        level = CategoryLevel.TOP,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                    ),
                )
            val bottomGarment =
                db.garmentDao().insert(
                    GarmentEntity(
                        name = "Trousers",
                        categoryId = bottomsCategory,
                        primaryColorId = null,
                        pattern = null,
                        fit = null,
                        length = null,
                        sleeveLength = null,
                        warmthRating = null,
                        breathabilityRating = null,
                        brandId = null,
                        size = null,
                        price = null,
                        currencyCode = null,
                        purchaseDate = null,
                        condition = null,
                        careNotes = null,
                        status = GarmentStatus.ACTIVE,
                        isReviewed = true,
                        searchText = "trousers",
                        createdAt = 0L,
                        updatedAt = 0L,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                    ),
                )
            db.wearEventDao().insert(wearEvent(date = "2026-06-01", garmentId = topGarment))
            db.wearEventDao().insert(wearEvent(date = "2026-06-02", garmentId = topGarment))
            db.wearEventDao().insert(wearEvent(date = "2026-06-03", garmentId = topGarment))
            db.wearEventDao().insert(wearEvent(date = "2026-06-04", garmentId = bottomGarment))

            val usage = repository.observeUsageStats(StatsWindow.ONE_MONTH).first()

            assertEquals(topsCategory, usage.favouriteCategoryIds.first().value)
            assertTrue(bottomsCategory in usage.favouriteCategoryIds.map { it.value })
        }

    @Test
    fun `wear heatmap counts one row per logged day and excludes planned events`() =
        runTest {
            val garment = insertGarment("Daily Item")
            db.wearEventDao().insert(wearEvent(date = "2026-06-06", garmentId = garment))
            db.wearEventDao().insert(wearEvent(date = "2026-06-06", garmentId = insertGarment("Other")))
            db.wearEventDao().insert(wearEvent(date = "2026-06-10", garmentId = garment))
            db.wearEventDao().insert(
                wearEvent(date = "2026-06-20", garmentId = garment, status = WearEventStatus.PLANNED),
            )

            val heatmap =
                repository
                    .observeWearHeatmap(DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                    .first()
                    .associate { it.date to it.wearCount }

            assertEquals(2, heatmap[LocalDate.of(2026, 6, 6)])
            assertEquals(1, heatmap[LocalDate.of(2026, 6, 10)])
            assertTrue(LocalDate.of(2026, 6, 20) !in heatmap)
        }

    @Test
    fun `repeated outfits only surfaces outfits worn more than once`() =
        runTest {
            val repeatedOutfit = insertOutfit("Repeated Look")
            val onceOutfit = insertOutfit("Single Wear Look")
            db.wearEventDao().insert(wearEvent(date = "2026-06-01", outfitId = repeatedOutfit))
            db.wearEventDao().insert(wearEvent(date = "2026-06-05", outfitId = repeatedOutfit))
            db.wearEventDao().insert(wearEvent(date = "2026-06-08", outfitId = repeatedOutfit))
            db.wearEventDao().insert(wearEvent(date = "2026-06-02", outfitId = onceOutfit))

            val repeated = repository.observeRepeatedOutfits(StatsWindow.ONE_MONTH).first()

            assertEquals(1, repeated.size)
            assertEquals(repeatedOutfit, repeated.single().outfitId.value)
            assertEquals(3, repeated.single().wearCount)
        }

    @Test
    fun `never worn outfits excludes archived and unsaved looks and anything already worn`() =
        runTest {
            val neverWorn = insertOutfit("Untouched Look")
            val alreadyWorn = insertOutfit("Worn Look")
            val archived = insertOutfit("Archived Look", isArchived = true)
            val unsaved = insertOutfit("AI Suggestion", isSaved = false)
            db.wearEventDao().insert(wearEvent(date = "2026-06-01", outfitId = alreadyWorn))

            val neverWornIds = repository.observeNeverWornOutfitIds().first().map { it.value }

            assertEquals(listOf(neverWorn), neverWornIds)
            assertTrue(archived !in neverWornIds && unsaved !in neverWornIds && alreadyWorn !in neverWornIds)
        }

    @Test
    fun `garments missing outfits excludes anything placed in a saved look`() =
        runTest {
            val inAnOutfit = insertGarment("Styled")
            val neverStyled = insertGarment("Unstyled")
            val outfitId = insertOutfit("A Look")
            val outfitDao = db.outfitDao()
            outfitDao.insertGarmentSlots(listOf(OutfitGarmentCrossRef(outfitId, layerSlot = 0, garmentId = inAnOutfit)))

            val missing = repository.observeGarmentsMissingOutfits().first().map { it.value }

            assertEquals(listOf(neverStyled), missing)
        }

    @Test
    fun `outfit wear event count only counts WORN events logged against an outfit`() =
        runTest {
            val outfitId = insertOutfit("Look")
            val garmentId = insertGarment("Solo Item")
            db.wearEventDao().insert(wearEvent(date = "2026-06-01", outfitId = outfitId))
            db.wearEventDao().insert(wearEvent(date = "2026-06-02", outfitId = outfitId))
            val wearEventDao = db.wearEventDao()
            wearEventDao.insert(wearEvent(date = "2026-06-03", outfitId = outfitId, status = WearEventStatus.PLANNED))
            db.wearEventDao().insert(wearEvent(date = "2026-06-04", garmentId = garmentId))

            val count = repository.observeOutfitWearEventCount(StatsWindow.ONE_MONTH).first()

            assertEquals(2, count)
        }

    @Test
    fun `top dress code differs between a real weekday and a real weekend date`() =
        runTest {
            val casualGarment = insertGarment("Jeans")
            val businessGarment = insertGarment("Suit")
            db.garmentDao().insertDressCodes(listOf(GarmentDressCodeCrossRef(casualGarment, DressCode.CASUAL)))
            db.garmentDao().insertDressCodes(listOf(GarmentDressCodeCrossRef(businessGarment, DressCode.BUSINESS)))
            // 2026-06-06 is a Saturday; 2026-06-10 is a Wednesday.
            db.wearEventDao().insert(wearEvent(date = "2026-06-06", garmentId = casualGarment))
            db.wearEventDao().insert(wearEvent(date = "2026-06-10", garmentId = businessGarment))

            val weekend = repository.observeTopDressCode(StatsWindow.ONE_MONTH, isWeekend = true).first()
            val weekday = repository.observeTopDressCode(StatsWindow.ONE_MONTH, isWeekend = false).first()

            assertEquals(DressCode.CASUAL, weekend)
            assertEquals(DressCode.BUSINESS, weekday)
        }

    @Test
    fun `a larger wear history still aggregates correctly through the heatmap`() =
        runTest {
            val garments = (1..20).map { insertGarment("Item $it") }
            var expectedWornCount = 0
            for (day in 1..28) {
                val date = "2026-06-" + day.toString().padStart(2, '0')
                // Two garments worn each day, from a rotating pool of 20.
                db.wearEventDao().insert(wearEvent(date = date, garmentId = garments[day % garments.size]))
                db.wearEventDao().insert(wearEvent(date = date, garmentId = garments[(day + 1) % garments.size]))
                expectedWornCount += 2
            }

            val heatmap =
                repository.observeWearHeatmap(DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28))).first()
            val costPerWear = repository.observeCostPerWear().first()

            assertEquals(28, heatmap.size)
            assertEquals(expectedWornCount, heatmap.sumOf { it.wearCount })
            assertEquals(expectedWornCount, costPerWear.sumOf { it.totalWearCount })
        }
}
