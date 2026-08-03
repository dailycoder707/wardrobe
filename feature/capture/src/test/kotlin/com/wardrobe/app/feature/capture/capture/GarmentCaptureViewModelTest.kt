package com.wardrobe.app.feature.capture.capture

import app.cash.turbine.test
import com.wardrobe.app.feature.capture.fakes.FakeImportQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GarmentCaptureViewModelTest {
    private lateinit var importQueueRepository: FakeImportQueueRepository
    private lateinit var viewModel: GarmentCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        importQueueRepository = FakeImportQueueRepository()
        viewModel = GarmentCaptureViewModel(importQueueRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `capturing a photo enqueues it and signals queued`() =
        runTest {
            viewModel.isQueued.test {
                assertEquals(false, awaitItem())
                viewModel.onPhotoCaptured("/tmp/captured.jpg")
                assertEquals(true, awaitItem())
            }
            assertEquals(listOf("/tmp/captured.jpg"), importQueueRepository.currentItems().map { it.sourceFilePath })
        }
}
