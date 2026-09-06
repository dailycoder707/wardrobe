package com.wardrobe.app.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportQueueRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: ImportQueueRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext<Context>())
        repository = ImportQueueRepositoryImpl(db.importQueueDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `enqueue inserts one PENDING row per file and returns them with assigned ids`() =
        runTest {
            val items = repository.enqueue(listOf("/tmp/a.jpg", "/tmp/b.jpg"))

            assertEquals(2, items.size)
            items.forEach { item ->
                assertEquals(ImportQueueItemStatus.PENDING, item.status)
                assertNull(item.stagingId)
                assertNull(item.savedGarmentId)
            }
            assertEquals(2, repository.observeQueue().first().size)
        }

    @Test
    fun `observeIncompleteCount excludes only COMPLETED rows`() =
        runTest {
            val items = repository.enqueue(listOf("/tmp/a.jpg", "/tmp/b.jpg"))
            assertEquals(2, repository.observeIncompleteCount().first())

            repository.updateItem(
                items[0].copy(status = ImportQueueItemStatus.COMPLETED, savedGarmentId = GarmentId(1)),
            )

            assertEquals(1, repository.observeIncompleteCount().first())
        }

    @Test
    fun `updateItem persists status and staging id transitions`() =
        runTest {
            val item = repository.enqueue(listOf("/tmp/a.jpg")).single()

            repository.updateItem(
                item.copy(status = ImportQueueItemStatus.EXTRACTING_GARMENT, stagingId = "staging-1"),
            )

            val reloaded = repository.observeQueue().first().single()
            assertEquals(ImportQueueItemStatus.EXTRACTING_GARMENT, reloaded.status)
            assertEquals("staging-1", reloaded.stagingId)
        }

    @Test
    fun `updateItem persists a failure reason`() =
        runTest {
            val item = repository.enqueue(listOf("/tmp/a.jpg")).single()

            repository.updateItem(item.copy(status = ImportQueueItemStatus.FAILED, errorMessage = "disk full"))

            val reloaded = repository.observeQueue().first().single()
            assertEquals(ImportQueueItemStatus.FAILED, reloaded.status)
            assertNotNull(reloaded.errorMessage)
        }

    @Test
    fun `deleteCompleted removes only COMPLETED rows`() =
        runTest {
            val items = repository.enqueue(listOf("/tmp/a.jpg", "/tmp/b.jpg"))
            repository.updateItem(
                items[0].copy(status = ImportQueueItemStatus.COMPLETED, savedGarmentId = GarmentId(1)),
            )

            repository.deleteCompleted()

            val remaining = repository.observeQueue().first()
            assertEquals(1, remaining.size)
            assertEquals(items[1].sourceFilePath, remaining.single().sourceFilePath)
        }
}
