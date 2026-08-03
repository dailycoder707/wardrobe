package com.wardrobe.app.feature.capture.queue

import com.wardrobe.app.core.domain.repository.ImageProcessingProgress
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import com.wardrobe.app.feature.capture.fakes.FakeImageRepository
import com.wardrobe.app.feature.capture.fakes.FakeImportQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GarmentImportQueueViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var importQueueRepository: FakeImportQueueRepository
    private lateinit var imageRepository: FakeImageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        importQueueRepository = FakeImportQueueRepository()
        imageRepository = FakeImageRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stagedImage(stagingId: String) =
        StagedImage(
            stagingId = stagingId,
            variants = emptyList(),
            qualityReport = QualityReport(emptyList()),
            backgroundRemovalStatus = BackgroundRemovalStatus.SUCCEEDED,
            cutoutConfidence = 0.9f,
        )

    @Test
    fun `processes items sequentially, never starting the second before the first completes`() =
        runTest(testDispatcher) {
            importQueueRepository.enqueue(listOf("/a.jpg", "/b.jpg"))
            GarmentImportQueueViewModel(importQueueRepository, imageRepository)
            advanceUntilIdle()

            assertEquals(listOf("/a.jpg"), imageRepository.stageImageCalls)

            imageRepository.channelsByPath.getValue("/a.jpg").apply {
                send(ImageProcessingProgress.Completed(stagedImage("staging-a")))
                close()
            }
            advanceUntilIdle()

            assertEquals(listOf("/a.jpg", "/b.jpg"), imageRepository.stageImageCalls)
        }

    @Test
    fun `a completed item is marked READY_FOR_REVIEW`() =
        runTest(testDispatcher) {
            importQueueRepository.enqueue(listOf("/a.jpg"))
            GarmentImportQueueViewModel(importQueueRepository, imageRepository)
            advanceUntilIdle()

            imageRepository.channelsByPath.getValue("/a.jpg").apply {
                send(ImageProcessingProgress.Completed(stagedImage("staging-a")))
                close()
            }
            advanceUntilIdle()

            val item = importQueueRepository.currentItems().single()
            assertEquals(ImportQueueItemStatus.READY_FOR_REVIEW, item.status)
        }

    @Test
    fun `a failed item is marked FAILED with its reason and retry restarts it`() =
        runTest(testDispatcher) {
            importQueueRepository.enqueue(listOf("/a.jpg"))
            val viewModel = GarmentImportQueueViewModel(importQueueRepository, imageRepository)
            advanceUntilIdle()

            imageRepository.channelsByPath.getValue("/a.jpg").apply {
                send(ImageProcessingProgress.Failed("disk full"))
                close()
            }
            advanceUntilIdle()

            val failed = importQueueRepository.currentItems().single()
            assertEquals(ImportQueueItemStatus.FAILED, failed.status)
            assertEquals("disk full", failed.errorMessage)

            viewModel.onRetry(failed.id)
            advanceUntilIdle()

            assertEquals(listOf("/a.jpg", "/a.jpg"), imageRepository.stageImageCalls)
        }

    @Test
    fun `a stale SAVING row left from a crash is reset to READY_FOR_REVIEW on init`() =
        runTest(testDispatcher) {
            val items = importQueueRepository.enqueue(listOf("/a.jpg"))
            importQueueRepository.updateItem(
                items.single().copy(status = ImportQueueItemStatus.SAVING, stagingId = "staging-a"),
            )

            GarmentImportQueueViewModel(importQueueRepository, imageRepository)
            advanceUntilIdle()

            val item = importQueueRepository.currentItems().single()
            assertEquals(ImportQueueItemStatus.READY_FOR_REVIEW, item.status)
        }
}
