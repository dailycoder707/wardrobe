package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.TripEntity
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Phase 9 Trip Intelligence — [TripRepositoryImpl.generatePackingSuggestions]
 * generates one real outfit per trip day (via [StylingEngineRepository],
 * mocked here since the actual recommendation logic already has its own
 * dedicated test suite), deduplicated across days, plus a toiletries
 * checklist and reminder items. Never a fabricated static list — every
 * `PackingListItem` this method returns traces back to a real
 * `SuggestionContext` call per day.
 */
@RunWith(RobolectricTestRunner::class)
class TripRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var stylingEngineRepository: StylingEngineRepository
    private lateinit var repository: TripRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        stylingEngineRepository = mockk()
        val lazyStyling = mockk<Lazy<StylingEngineRepository>> { every { get() } returns stylingEngineRepository }
        repository = TripRepositoryImpl(db.tripDao(), lazyStyling)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun scoredOutfitWith(vararg garmentIds: Long): ScoredOutfit =
        ScoredOutfit(
            outfit =
                Outfit(
                    id = OutfitId(0),
                    name = null,
                    garments = garmentIds.mapIndexed { index, id -> OutfitGarmentSlot(GarmentId(id), index) },
                    occasionId = null,
                    source = OutfitSource.AI_SUGGESTED,
                    isSaved = false,
                    photoUri = null,
                    createdAt = Instant.now(),
                ),
            score = 1.0,
            explanation = "test outfit",
            passedWeatherFilter = true,
        )

    @Test
    fun `generatePackingSuggestions produces one deduplicated outfit item per day plus toiletries and reminders`() =
        runTest {
            val tripId =
                db.tripDao().insert(
                    TripEntity(
                        name = "City Break",
                        destination = "Paris",
                        startDate = "2026-07-01",
                        endDate = "2026-07-03",
                        luggageSize = LuggageSize.CARRY_ON,
                        createdAt = 0L,
                    ),
                )
            // Same outfit (garments 1, 2) recommended every day — must be
            // deduplicated across the trip's 3 days, not repeated per day.
            coEvery { stylingEngineRepository.suggestOutfits(any()) } returns listOf(scoredOutfitWith(1, 2))

            val suggestions = repository.generatePackingSuggestions(TripId(tripId))

            val outfitItems = suggestions.filter { it.garmentId != null }
            assertEquals(setOf(GarmentId(1), GarmentId(2)), outfitItems.map { it.garmentId }.toSet())
            assertEquals(2, outfitItems.size)

            val toiletries = suggestions.filter { it.category == "Toiletries" }
            assertTrue(toiletries.isNotEmpty())

            val reminders = suggestions.filter { it.category == "Reminders" }
            assertTrue(reminders.any { it.freeTextName?.contains("liquid", ignoreCase = true) == true })
        }

    @Test
    fun `generatePackingSuggestions returns nothing for a trip that does not exist`() =
        runTest {
            val suggestions = repository.generatePackingSuggestions(TripId(999))
            assertTrue(suggestions.isEmpty())
        }
}
