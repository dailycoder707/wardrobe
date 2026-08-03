package com.wardrobe.app.core.data.sync.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val noopResolver = SyncIdResolver { _, _ -> null }

/**
 * [BodyProfileSyncHandler] is the one handler whose payload embeds a real
 * nested collection (reference photos) and a nested 1:1 record
 * (measurements) rather than a flat set of scalar fields — these tests
 * focus on that embedding round-tripping correctly, on top of the same LWW/
 * conflict/safe-delete rules [TagSyncHandlerTest] already covers for every
 * handler.
 */
@RunWith(RobolectricTestRunner::class)
class BodyProfileSyncHandlerTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var handler: BodyProfileSyncHandler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = createInMemoryWardrobeDatabase(context)
        handler = BodyProfileSyncHandler(db.bodyProfileDao(), BodyImageFileStore(context), ImageFileStore(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a remote change newer than the local row is applied`() =
        runTest {
            db.bodyProfileDao().insertProfile(
                BodyProfileEntity(label = "Me", createdAt = 100, syncId = "profile-1", updatedAt = 100),
            )

            val fieldsJson = """{"label":"Renamed","createdAt":100,"photos":[],"measurements":null}"""
            val outcome = handler.applyUpsert("profile-1", fieldsJson, remoteUpdatedAt = 200, noopResolver)

            assertTrue(outcome is ApplyOutcome.Applied)
            assertEquals("Renamed", db.bodyProfileDao().getProfileBySyncId("profile-1")?.label)
        }

    @Test
    fun `a remote change older than the local row is ignored, not overwritten`() =
        runTest {
            db.bodyProfileDao().insertProfile(
                BodyProfileEntity(label = "Me", createdAt = 100, syncId = "profile-1", updatedAt = 200),
            )

            val fieldsJson = """{"label":"Renamed","createdAt":100,"photos":[],"measurements":null}"""
            val outcome = handler.applyUpsert("profile-1", fieldsJson, remoteUpdatedAt = 100, noopResolver)

            assertTrue(outcome is ApplyOutcome.LocalNewerIgnored)
            assertEquals("Me", db.bodyProfileDao().getProfileBySyncId("profile-1")?.label)
        }

    @Test
    fun `applying an upsert with embedded measurements writes the measurements row too`() =
        runTest {
            val fieldsJson =
                """
                {"label":"Me","createdAt":100,"photos":[],
                 "measurements":{"shoulderWidthFraction":0.3,"torsoHeightFraction":null,
                 "waistHeightFraction":null,"hipWidthFraction":null,"neckPositionYFraction":null,
                 "anklePositionYFraction":null,"confidence":0.8,"source":"POSE_DETECTION","computedAt":150}}
                """.trimIndent()

            val outcome = handler.applyUpsert("profile-1", fieldsJson, remoteUpdatedAt = 100, noopResolver)

            assertTrue(outcome is ApplyOutcome.Applied)
            val profileId = requireNotNull(db.bodyProfileDao().getProfileBySyncId("profile-1")).id
            val measurements = db.bodyProfileDao().getMeasurements(profileId)
            assertNotNull(measurements)
            assertEquals(0.3f, measurements?.shoulderWidthFraction)
            assertEquals("POSE_DETECTION", measurements?.source)
        }

    @Test
    fun `currentFieldsJson round-trips photos and measurements back out`() =
        runTest {
            val fieldsJson =
                """
                {"label":"Me","createdAt":100,
                 "photos":[{"pose":"NEUTRAL","width":10,"height":20,"checksum":null}],
                 "measurements":null}
                """.trimIndent()
            handler.applyUpsert("profile-1", fieldsJson, remoteUpdatedAt = 100, noopResolver)

            val roundTripped = handler.currentFieldsJson("profile-1")

            assertNotNull(roundTripped)
            assertTrue(roundTripped!!.contains("\"pose\":\"NEUTRAL\""))
        }

    @Test
    fun `deleting a row the local device edited more recently surfaces a conflict instead of deleting it`() =
        runTest {
            db.bodyProfileDao().insertProfile(
                BodyProfileEntity(label = "Me", createdAt = 100, syncId = "profile-1", updatedAt = 200),
            )

            val outcome = handler.applyDelete("profile-1", remoteDeletedAt = 100)

            assertTrue(outcome is ApplyOutcome.EditDeleteConflict)
            assertNotNull("the row must survive an unsafe delete", db.bodyProfileDao().getProfileBySyncId("profile-1"))
        }

    @Test
    fun `deleting a row with no local edit since the remote delete removes it safely`() =
        runTest {
            db.bodyProfileDao().insertProfile(
                BodyProfileEntity(label = "Me", createdAt = 100, syncId = "profile-1", updatedAt = 100),
            )

            val outcome = handler.applyDelete("profile-1", remoteDeletedAt = 200)

            assertTrue(outcome is ApplyOutcome.Applied)
            assertNull(db.bodyProfileDao().getProfileBySyncId("profile-1"))
        }
}
