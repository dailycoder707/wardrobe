package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class OutfitRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: OutfitRepositoryImpl
    private var categoryId = 0L

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        repository = OutfitRepositoryImpl(db.outfitDao())
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
                    syncId =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                ),
            )
        return GarmentId(id)
    }

    private fun outfit(
        garmentIds: List<GarmentId>,
        name: String? = "Weekend Look",
    ) = Outfit(
        id = OutfitId(0),
        name = name,
        garments = garmentIds.mapIndexed { index, id -> OutfitGarmentSlot(id, index) },
        occasionId = null,
        source = OutfitSource.USER_CREATED,
        isSaved = true,
        seasons = setOf(Season.SUMMER),
        dressCodes = setOf(DressCode.CASUAL),
        photoUri = null,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `saving an outfit persists garment slots and seasons dress codes and tags`() =
        runTest {
            val top = insertGarment("Top")
            val bottom = insertGarment("Bottom")

            val saved = repository.saveOutfit(outfit(listOf(top, bottom)))
            val reloaded = repository.getOutfit(saved)

            requireNotNull(reloaded)
            assertEquals(listOf(top, bottom), reloaded.garments.sortedBy { it.layerSlot }.map { it.garmentId })
            assertEquals(setOf(Season.SUMMER), reloaded.seasons)
            assertEquals(setOf(DressCode.CASUAL), reloaded.dressCodes)
        }

    @Test
    fun `re-saving an existing outfit replaces its garment slots rather than appending`() =
        runTest {
            val top = insertGarment("Top")
            val shoes = insertGarment("Shoes")

            val id = repository.saveOutfit(outfit(listOf(top)))
            val original = requireNotNull(repository.getOutfit(id))
            repository.saveOutfit(
                original.copy(garments = listOf(OutfitGarmentSlot(shoes, OutfitSlot.SHOES.slotIndex))),
            )

            val reloaded = requireNotNull(repository.getOutfit(id))
            assertEquals(listOf(shoes), reloaded.garments.map { it.garmentId })
        }

    @Test
    fun `favorite and archive flags are toggled independently`() =
        runTest {
            val top = insertGarment("Top")
            val id = repository.saveOutfit(outfit(listOf(top)))

            repository.setFavorite(id, true)
            assertTrue(requireNotNull(repository.getOutfit(id)).isFavorite)

            repository.setArchived(id, true)
            val archived = requireNotNull(repository.getOutfit(id))
            assertTrue(archived.isArchived)
            assertTrue(archived.isFavorite)
        }

    @Test
    fun `observeOutfits filters archived outfits out by default`() =
        runTest {
            val top = insertGarment("Top")
            val visible = repository.saveOutfit(outfit(listOf(top), name = "Visible"))
            val archived = repository.saveOutfit(outfit(listOf(top), name = "Archived"))
            repository.setArchived(archived, true)

            val results = repository.observeOutfits(OutfitFilter()).first()

            assertEquals(listOf(visible), results.map { it.id })
        }

    @Test
    fun `observeOutfit emits null for a deleted or missing outfit`() =
        runTest {
            val missing = repository.observeOutfit(OutfitId(999)).first()
            assertNull(missing)
        }
}
