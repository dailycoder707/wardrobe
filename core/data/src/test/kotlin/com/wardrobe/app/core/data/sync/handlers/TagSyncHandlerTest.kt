package com.wardrobe.app.core.data.sync.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
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
 * [TagSyncHandler] is the simplest of the sixteen handlers — its own tests
 * exercise the three rules every other handler shares (LWW, edit/delete
 * conflict, safe delete) without the noise of Garment's foreign keys and
 * collections, which [GarmentSyncHandlerTest] covers instead.
 */
@RunWith(RobolectricTestRunner::class)
class TagSyncHandlerTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var handler: TagSyncHandler

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext<Context>())
        handler = TagSyncHandler(db.tagDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a remote change newer than the local row is applied`() =
        runTest {
            db.tagDao().insert(TagEntity(name = "Casual", syncId = "tag-1", updatedAt = 100))

            val outcome = handler.applyUpsert("tag-1", """{"name":"Formal"}""", remoteUpdatedAt = 200, noopResolver)

            assertTrue(outcome is ApplyOutcome.Applied)
            assertEquals("Formal", db.tagDao().getBySyncId("tag-1")?.name)
        }

    @Test
    fun `a remote change older than the local row is ignored, not overwritten`() =
        runTest {
            db.tagDao().insert(TagEntity(name = "Casual", syncId = "tag-1", updatedAt = 200))

            val outcome = handler.applyUpsert("tag-1", """{"name":"Formal"}""", remoteUpdatedAt = 100, noopResolver)

            assertTrue(outcome is ApplyOutcome.LocalNewerIgnored)
            assertEquals("Casual", db.tagDao().getBySyncId("tag-1")?.name)
        }

    @Test
    fun `an upsert for an unseen syncId inserts a new local row`() =
        runTest {
            val outcome =
                handler.applyUpsert(
                    "brand-new-tag",
                    """{"name":"Vacation"}""",
                    remoteUpdatedAt = 50,
                    noopResolver,
                )

            assertTrue(outcome is ApplyOutcome.Applied)
            assertNotNull(db.tagDao().getBySyncId("brand-new-tag"))
        }

    @Test
    fun `deleting a row the local device edited more recently surfaces a conflict instead of deleting it`() =
        runTest {
            db.tagDao().insert(TagEntity(name = "Renamed locally", syncId = "tag-1", updatedAt = 200))

            val outcome = handler.applyDelete("tag-1", remoteDeletedAt = 100)

            assertTrue(outcome is ApplyOutcome.EditDeleteConflict)
            assertNotNull("the row must survive an unsafe delete", db.tagDao().getBySyncId("tag-1"))
        }

    @Test
    fun `deleting a row with no local edit since the remote delete removes it safely`() =
        runTest {
            db.tagDao().insert(TagEntity(name = "Casual", syncId = "tag-1", updatedAt = 100))

            val outcome = handler.applyDelete("tag-1", remoteDeletedAt = 200)

            assertTrue(outcome is ApplyOutcome.Applied)
            assertNull(db.tagDao().getBySyncId("tag-1"))
        }

    @Test
    fun `deleting a syncId that no longer exists locally is a harmless no-op`() =
        runTest {
            val outcome = handler.applyDelete("never-existed", remoteDeletedAt = 100)

            assertTrue(outcome is ApplyOutcome.Applied)
        }
}
