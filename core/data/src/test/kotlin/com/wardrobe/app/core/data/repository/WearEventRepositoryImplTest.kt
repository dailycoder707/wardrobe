package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.wear.WearEvent
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
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WearEventRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: WearEventRepositoryImpl
    private var categoryId = 0L

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        repository = WearEventRepositoryImpl(db.wearEventDao())
    }

    private suspend fun insertGarment(name: String): GarmentId {
        if (categoryId == 0L) {
            categoryId =
                db.categoryDao().insert(CategoryEntity(name = "Tops", parentId = null, level = CategoryLevel.TOP))
        }
        val id =
            db.garmentDao().insert(
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
                ),
            )
        return GarmentId(id)
    }

    private fun wear(
        garmentId: GarmentId,
        date: LocalDate,
        status: WearEventStatus = WearEventStatus.WORN,
    ) = WearEvent(
        id = WearEventId(0),
        date = date,
        garmentId = garmentId,
        outfitId = null,
        weatherCacheId = null,
        occasionId = null,
        note = null,
        status = status,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `updateWear edits a row in place rather than creating a new one`() =
        runTest {
            val garment = insertGarment("Coat")
            val id = repository.logWear(wear(garment, LocalDate.of(2026, 6, 1), WearEventStatus.PLANNED))

            val planned =
                repository
                    .observeEvents(
                        DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
                    ).first()
            repository.updateWear(planned.single().copy(status = WearEventStatus.WORN))

            val events = repository.observeEvents(DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1))).first()
            assertEquals(1, events.size)
            assertEquals(id, events.single().id)
            assertEquals(WearEventStatus.WORN, events.single().status)
        }

    @Test
    fun `clearDay removes every event on that date only`() =
        runTest {
            val garment = insertGarment("Coat")
            repository.logWear(wear(garment, LocalDate.of(2026, 6, 1)))
            repository.logWear(wear(garment, LocalDate.of(2026, 6, 2)))

            repository.clearDay(LocalDate.of(2026, 6, 1))

            val range = DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))
            val remaining = repository.observeEvents(range).first()
            assertEquals(listOf(LocalDate.of(2026, 6, 2)), remaining.map { it.date })
        }

    @Test
    fun `duplicateDay copies events as planned without touching the source day`() =
        runTest {
            val garment = insertGarment("Coat")
            repository.logWear(wear(garment, LocalDate.of(2026, 6, 1), WearEventStatus.WORN))

            repository.duplicateDay(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8))

            val range = DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8))
            val events = repository.observeEvents(range).first()
            assertEquals(2, events.size)
            val original = events.single { it.date == LocalDate.of(2026, 6, 1) }
            val copy = events.single { it.date == LocalDate.of(2026, 6, 8) }
            assertEquals(WearEventStatus.WORN, original.status)
            assertEquals(WearEventStatus.PLANNED, copy.status)
            assertTrue(original.id != copy.id)
        }
}
