package com.wardrobe.app.core.data.sync.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers what [TagSyncHandlerTest] can't: foreign-key resolution (a garment
 * references its category by syncId, never a raw local id) and the
 * independent "collections always merge" rule — Garment is the richest of
 * the sixteen handlers, carrying five collections at once.
 */
@RunWith(RobolectricTestRunner::class)
class GarmentSyncHandlerTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var handler: GarmentSyncHandler
    private lateinit var resolver: SyncIdResolver

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext<Context>())
        handler =
            GarmentSyncHandler(
                db.garmentDao(),
                db.categoryDao(),
                db.colorDao(),
                db.brandDao(),
                db.materialDao(),
                db.tagDao(),
                db.fabricDao(),
                db.occasionDao(),
            )
        resolver =
            SyncIdResolver { table, syncId ->
                when (table) {
                    "categories" -> db.categoryDao().getBySyncId(syncId)?.id
                    "colors" -> db.colorDao().getBySyncId(syncId)?.id
                    "brands" -> db.brandDao().getBySyncId(syncId)?.id
                    "materials" -> db.materialDao().getBySyncId(syncId)?.id
                    "tags" -> db.tagDao().getBySyncId(syncId)?.id
                    "garments" -> db.garmentDao().getBySyncId(syncId)?.id
                    "fabrics" -> db.fabricDao().getBySyncId(syncId)?.id
                    "occasions" -> db.occasionDao().getBySyncId(syncId)?.id
                    else -> null
                }
            }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCategory(syncId: String): Long =
        db.categoryDao().insert(
            CategoryEntity(name = "Tops", parentId = null, level = CategoryLevel.TOP, syncId = syncId, updatedAt = 1),
        )

    private fun garmentWireJson(categorySyncId: String) =
        """
        {
            "name": "Blue Shirt",
            "categorySyncId": "$categorySyncId",
            "primaryColorSyncId": null,
            "pattern": null,
            "fit": null,
            "length": null,
            "sleeveLength": null,
            "warmthRating": null,
            "breathabilityRating": null,
            "brandSyncId": null,
            "size": null,
            "price": null,
            "currencyCode": null,
            "purchaseDate": null,
            "condition": null,
            "careNotes": null,
            "notes": null,
            "status": "ACTIVE",
            "isReviewed": true,
            "isFavorite": false,
            "isInLaundry": false,
            "searchText": "blue shirt",
            "createdAt": 1000,
            "palette": [],
            "materials": [],
            "tagSyncIds": [],
            "seasons": [],
            "dressCodes": [],
            "secondaryColorSyncId": null,
            "neckline": null,
            "gender": null,
            "waterproofLevel": null,
            "fabrics": [],
            "occasionSyncIds": []
        }
        """.trimIndent()

    @Test
    fun `a garment referencing a category unknown to this device defers rather than crashing or dropping data`() =
        runTest {
            val outcome = handler.applyUpsert("garment-1", garmentWireJson("category-not-yet-synced"), 100, resolver)

            assertTrue(
                "must defer, not fail outright, when the category hasn't arrived yet",
                outcome is ApplyOutcome.LocalNewerIgnored,
            )
        }

    @Test
    fun `once its category exists locally, an incoming garment resolves the foreign key correctly`() =
        runTest {
            val categoryId = insertCategory("category-1")

            val outcome = handler.applyUpsert("garment-1", garmentWireJson("category-1"), 100, resolver)

            assertTrue(outcome is ApplyOutcome.Applied)
            val stored = db.garmentDao().getBySyncId("garment-1")
            assertEquals(categoryId, stored?.categoryId)
            assertEquals("Blue Shirt", stored?.name)
        }

    @Test
    fun `tags merge across devices even when the incoming scalar fields are older and ignored`() =
        runTest {
            val categoryId = insertCategory("category-1")
            val localTagId = db.tagDao().insert(TagEntity(name = "Summer", syncId = "tag-summer", updatedAt = 1))
            db.tagDao().insert(TagEntity(name = "Favorite", syncId = "tag-favorite", updatedAt = 1))

            val existingGarment =
                GarmentEntity(
                    name = "Original Name",
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
                    searchText = "original",
                    createdAt = 1000,
                    updatedAt = 500,
                    syncId = "garment-1",
                )
            val garmentId = db.garmentDao().insert(existingGarment)
            db.garmentDao().insertTags(listOf(GarmentTagCrossRef(garmentId, localTagId)))

            val incomingWithOlderScalarsButANewTag =
                garmentWireJson("category-1").replace("\"tagSyncIds\": []", "\"tagSyncIds\": [\"tag-favorite\"]")

            val outcome =
                handler.applyUpsert(
                    "garment-1",
                    incomingWithOlderScalarsButANewTag,
                    remoteUpdatedAt = 50,
                    resolver,
                )

            assertTrue("older scalar fields must be ignored", outcome is ApplyOutcome.LocalNewerIgnored)
            assertEquals(
                "scalar fields must not have changed",
                "Original Name",
                db.garmentDao().getBySyncId("garment-1")?.name,
            )
            val relations = db.garmentDao().getWithRelations(garmentId)
            val tagIds = relations?.tags?.map { it.tagId }.orEmpty()
            assertTrue("the pre-existing tag must survive the merge", tagIds.contains(localTagId))
            assertEquals("both tags must be present after merging", 2, tagIds.size)
        }
}
