package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.BrandEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.ColorPaletteEntry
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.MaterialComposition
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class GarmentRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: GarmentRepositoryImpl

    private var categoryId = 0L
    private var colorId = 0L
    private var materialId = 0L
    private var brandId = 0L
    private var tagId = 0L

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        repository =
            GarmentRepositoryImpl(
                garmentDao = db.garmentDao(),
                categoryDao = db.categoryDao(),
                colorDao = db.colorDao(),
                materialDao = db.materialDao(),
                brandDao = db.brandDao(),
                tagDao = db.tagDao(),
                imageFileStore = ImageFileStore(ApplicationProvider.getApplicationContext()),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedReferenceData() {
        categoryId =
            db.categoryDao().insert(
                CategoryEntity(
                    name = "Tops",
                    parentId = null,
                    level = com.wardrobe.app.core.model.garment.CategoryLevel.TOP,
                ),
            )
        colorId = db.colorDao().insert(ColorEntity(name = "Cream", hexValue = "#FFF8E7"))
        materialId = db.materialDao().insert(MaterialEntity(name = "Silk"))
        brandId = db.brandDao().insert(BrandEntity(name = "Reformation", logoUri = null))
        tagId = db.tagDao().insert(TagEntity(name = "office"))
    }

    @Test
    fun `save then get round-trips every field including cross-refs`() =
        runTest {
            seedReferenceData()
            val now = Instant.ofEpochMilli(1_700_000_000_000L)

            val garment =
                Garment(
                    id = GarmentId(0),
                    name = "Cream Silk Blouse",
                    categoryId = CategoryId(categoryId),
                    primaryColorId = ColorId(colorId),
                    palette =
                        listOf(
                            ColorPaletteEntry(Color(ColorId(colorId), "Cream", "#FFF8E7"), weightPercent = 100),
                        ),
                    materials =
                        listOf(
                            MaterialComposition(Material(MaterialId(materialId), "Silk"), percentage = 100),
                        ),
                    tagIds = listOf(TagId(tagId)),
                    seasons = setOf(Season.SPRING, Season.SUMMER),
                    dressCodes = setOf(DressCode.SMART_CASUAL),
                    pattern = null,
                    fit = null,
                    length = null,
                    sleeveLength = null,
                    warmthRating = 2,
                    breathabilityRating = 4,
                    brandId = BrandId(brandId),
                    size = "M",
                    price = null,
                    purchaseDate = null,
                    condition = null,
                    careNotes = "Dry clean only",
                    status = GarmentStatus.ACTIVE,
                    isReviewed = true,
                    isFavorite = false,
                    images = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )

            val savedId = repository.saveGarment(garment)
            val loaded = repository.getGarment(savedId)

            assertNotNull(loaded)
            assertEquals("Cream Silk Blouse", loaded!!.name)
            assertEquals(CategoryId(categoryId), loaded.categoryId)
            assertEquals(setOf(Season.SPRING, Season.SUMMER), loaded.seasons)
            assertEquals(setOf(DressCode.SMART_CASUAL), loaded.dressCodes)
            assertEquals(1, loaded.palette.size)
            assertEquals(
                "Cream",
                loaded.palette
                    .first()
                    .color.name,
            )
            assertEquals(100, loaded.palette.first().weightPercent)
            assertEquals(1, loaded.materials.size)
            assertEquals(
                "Silk",
                loaded.materials
                    .first()
                    .material.name,
            )
            assertEquals(listOf(TagId(tagId)), loaded.tagIds)
            assertEquals("Dry clean only", loaded.careNotes)
            assertEquals(GarmentStatus.ACTIVE, loaded.status)
        }

    @Test
    fun `season filter only returns garments tagged with that season`() =
        runTest {
            seedReferenceData()
            val now = Instant.now()

            fun baseGarment(seasons: Set<Season>) =
                Garment(
                    id = GarmentId(0),
                    name = null,
                    categoryId = CategoryId(categoryId),
                    primaryColorId = null,
                    palette = emptyList(),
                    materials = emptyList(),
                    tagIds = emptyList(),
                    seasons = seasons,
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
                    isReviewed = false,
                    isFavorite = false,
                    images = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )

            repository.saveGarment(baseGarment(setOf(Season.WINTER)))
            repository.saveGarment(baseGarment(setOf(Season.SUMMER)))

            val summerOnly = repository.observeGarments(GarmentFilter(season = Season.SUMMER, status = null)).first()
            assertEquals(1, summerOnly.size)
            assertEquals(setOf(Season.SUMMER), summerOnly.first().seasons)
        }

    @Test
    fun `deleting a garment with no wear history succeeds`() =
        runTest {
            seedReferenceData()
            val now = Instant.now()
            val garment =
                Garment(
                    id = GarmentId(0),
                    name = "Plain Tee",
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
                    price = null,
                    purchaseDate = null,
                    condition = null,
                    careNotes = null,
                    status = GarmentStatus.ACTIVE,
                    isReviewed = false,
                    isFavorite = false,
                    images = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )
            val id = repository.saveGarment(garment)
            repository.deleteGarment(id)
            assertNotNull(repository.getGarment(id) == null)
        }

    @Test
    fun `setInLaundry toggles the flag without touching any other field`() =
        runTest {
            seedReferenceData()
            val now = Instant.now()
            val garment =
                Garment(
                    id = GarmentId(0),
                    name = "Wool Sweater",
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
                    price = null,
                    purchaseDate = null,
                    condition = null,
                    careNotes = null,
                    status = GarmentStatus.ACTIVE,
                    isReviewed = false,
                    isFavorite = true,
                    images = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )
            val id = repository.saveGarment(garment)

            repository.setInLaundry(id, true)

            val updated = repository.getGarment(id)
            assertEquals(true, updated?.isInLaundry)
            assertEquals(true, updated?.isFavorite)
            assertEquals("Wool Sweater", updated?.name)
        }

    private fun garmentFor(
        category: Long,
        color: Long?,
        status: GarmentStatus = GarmentStatus.ACTIVE,
        name: String? = null,
    ): Garment {
        val now = Instant.now()
        return Garment(
            id = GarmentId(0),
            name = name,
            categoryId = CategoryId(category),
            primaryColorId = color?.let(::ColorId),
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
            status = status,
            isReviewed = false,
            isFavorite = false,
            images = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `findPotentialDuplicates matches on category and primary color`() =
        runTest {
            seedReferenceData()
            val otherColorId =
                db.colorDao().insert(ColorEntity(name = "Black", hexValue = "#000000", syncId = "color-black"))
            val matchId = repository.saveGarment(garmentFor(categoryId, colorId, name = "Match"))
            repository.saveGarment(garmentFor(categoryId, otherColorId, name = "Different Color"))

            val duplicates =
                repository.findPotentialDuplicates(CategoryId(categoryId), ColorId(colorId), excludeGarmentId = null)

            assertEquals(listOf(matchId), duplicates.map { it.id })
        }

    @Test
    fun `findPotentialDuplicates excludes a different category`() =
        runTest {
            seedReferenceData()
            val otherCategoryId =
                db.categoryDao().insert(
                    CategoryEntity(
                        name = "Bottoms",
                        parentId = null,
                        level = com.wardrobe.app.core.model.garment.CategoryLevel.TOP,
                        syncId = "category-bottoms",
                    ),
                )
            repository.saveGarment(garmentFor(otherCategoryId, colorId, name = "Wrong Category"))

            val duplicates =
                repository.findPotentialDuplicates(CategoryId(categoryId), ColorId(colorId), excludeGarmentId = null)

            assertEquals(emptyList<Garment>(), duplicates)
        }

    @Test
    fun `findPotentialDuplicates excludes the garment being edited`() =
        runTest {
            seedReferenceData()
            val id = repository.saveGarment(garmentFor(categoryId, colorId, name = "Self"))

            val duplicates =
                repository.findPotentialDuplicates(CategoryId(categoryId), ColorId(colorId), excludeGarmentId = id)

            assertEquals(emptyList<Garment>(), duplicates)
        }

    @Test
    fun `findPotentialDuplicates excludes non-ACTIVE status`() =
        runTest {
            seedReferenceData()
            repository.saveGarment(garmentFor(categoryId, colorId, status = GarmentStatus.DONATING, name = "Donating"))

            val duplicates =
                repository.findPotentialDuplicates(CategoryId(categoryId), ColorId(colorId), excludeGarmentId = null)

            assertEquals(emptyList<Garment>(), duplicates)
        }
}
