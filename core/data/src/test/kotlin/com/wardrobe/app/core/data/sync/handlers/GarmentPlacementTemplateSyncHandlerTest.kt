package com.wardrobe.app.core.data.sync.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [GarmentPlacementTemplateSyncHandler] resolves two foreign keys (body
 * profile + garment) before it can apply anything — these tests focus on
 * that resolution, on top of the same LWW/conflict/safe-delete rules every
 * handler shares (already covered in depth by [TagSyncHandlerTest]).
 */
@RunWith(RobolectricTestRunner::class)
class GarmentPlacementTemplateSyncHandlerTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var handler: GarmentPlacementTemplateSyncHandler
    private var bodyProfileId: Long = 0
    private var garmentId: Long = 0

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = createInMemoryWardrobeDatabase(context)
            handler =
                GarmentPlacementTemplateSyncHandler(
                    db.garmentPlacementTemplateDao(),
                    db.bodyProfileDao(),
                    db.garmentDao(),
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
        SyncIdResolver { table, syncId ->
            when (table) {
                "body_profiles" -> bodyProfileId
                "garments" -> garmentId
                else -> null
            }
        }

    private fun wireJson(offsetX: Float = 0.5f) =
        """
        {"bodyProfileSyncId":"profile-1","garmentSyncId":"garment-1","templateType":"DEFAULT",
         "customName":null,"offsetXFraction":$offsetX,"offsetYFraction":0.3,"scale":1.0,
         "rotationDegrees":0.0,"isUserAdjusted":false,"placementSource":"DEFAULT_HEURISTIC","lastUsedAt":null}
        """.trimIndent()

    @Test
    fun `an upsert with resolvable body profile and garment ids is applied`() =
        runTest {
            val outcome = handler.applyUpsert("template-1", wireJson(), remoteUpdatedAt = 100, resolver())

            assertTrue(outcome is ApplyOutcome.Applied)
            val stored = db.garmentPlacementTemplateDao().getBySyncId("template-1")
            assertEquals(bodyProfileId, stored?.bodyProfileId)
            assertEquals(garmentId, stored?.garmentId)
        }

    @Test
    fun `an upsert whose garment cannot yet be resolved is deferred, not applied`() =
        runTest {
            val unresolvedResolver =
                SyncIdResolver { table, _ ->
                    if (table ==
                        "body_profiles"
                    ) {
                        bodyProfileId
                    } else {
                        null
                    }
                }

            val outcome = handler.applyUpsert("template-1", wireJson(), remoteUpdatedAt = 100, unresolvedResolver)

            assertTrue(outcome is ApplyOutcome.LocalNewerIgnored)
            assertNull(db.garmentPlacementTemplateDao().getBySyncId("template-1"))
        }

    @Test
    fun `a remote change older than the local row is ignored, not overwritten`() =
        runTest {
            db.garmentPlacementTemplateDao().insert(
                GarmentPlacementTemplateEntity(
                    bodyProfileId = bodyProfileId,
                    garmentId = garmentId,
                    templateType = "DEFAULT",
                    customName = null,
                    offsetXFraction = 0.1f,
                    offsetYFraction = 0.1f,
                    scale = 1f,
                    rotationDegrees = 0f,
                    isUserAdjusted = true,
                    placementSource = "DEFAULT_HEURISTIC",
                    lastUsedAt = null,
                    updatedAt = 200,
                    syncId = "template-1",
                ),
            )

            val outcome = handler.applyUpsert("template-1", wireJson(offsetX = 0.9f), remoteUpdatedAt = 100, resolver())

            assertTrue(outcome is ApplyOutcome.LocalNewerIgnored)
            assertEquals(0.1f, db.garmentPlacementTemplateDao().getBySyncId("template-1")?.offsetXFraction)
        }
}
