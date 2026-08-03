package com.wardrobe.app.core.data.sync.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [GarmentMaskSyncHandler] mirrors [GarmentPlacementTemplateSyncHandler]'s
 * two-FK resolution — these tests skip real checksum-based file transfer
 * (covered structurally by [placeFileForChecksum]'s own early-return when
 * `checksum == null`) and focus on the row-level apply/resolve/LWW logic. */
@RunWith(RobolectricTestRunner::class)
class GarmentMaskSyncHandlerTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var handler: GarmentMaskSyncHandler
    private var bodyProfileId: Long = 0
    private var garmentId: Long = 0

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = createInMemoryWardrobeDatabase(context)
            handler =
                GarmentMaskSyncHandler(
                    db.garmentMaskDao(),
                    db.bodyProfileDao(),
                    db.garmentDao(),
                    BodyImageFileStore(context),
                    ImageFileStore(context),
                )

            bodyProfileId =
                db.bodyProfileDao().insertProfile(BodyProfileEntity(label = "Me", createdAt = 0, syncId = "profile-1"))
            val categoryId =
                db.categoryDao().insert(
                    CategoryEntity(name = "Tops", parentId = null, level = CategoryLevel.TOP, syncId = "cat-1"),
                )
            garmentId =
                db.garmentDao().insert(
                    GarmentEntity(
                        name = "Shirt",
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
                        searchText = "shirt",
                        createdAt = 0,
                        updatedAt = 0,
                        syncId = "garment-1",
                    ),
                )
        }

    @After
    fun tearDown() {
        db.close()
    }

    private fun resolver() =
        SyncIdResolver { table, _ ->
            when (table) {
                "body_profiles" -> bodyProfileId
                "garments" -> garmentId
                else -> null
            }
        }

    private val wireJson = """{"bodyProfileSyncId":"profile-1","garmentSyncId":"garment-1","checksum":null}"""

    @Test
    fun `an upsert with resolvable ids is applied and readable back out`() =
        runTest {
            val outcome = handler.applyUpsert("mask-1", wireJson, remoteUpdatedAt = 100, resolver())

            assertTrue(outcome is ApplyOutcome.Applied)
            val stored = db.garmentMaskDao().getBySyncId("mask-1")
            assertEquals(bodyProfileId, stored?.bodyProfileId)
            assertEquals(garmentId, stored?.garmentId)
        }

    @Test
    fun `deleting a mask with no local edit since the remote delete removes it safely`() =
        runTest {
            handler.applyUpsert("mask-1", wireJson, remoteUpdatedAt = 100, resolver())

            val outcome = handler.applyDelete("mask-1", remoteDeletedAt = 200)

            assertTrue(outcome is ApplyOutcome.Applied)
            assertNull(db.garmentMaskDao().getBySyncId("mask-1"))
        }
}
