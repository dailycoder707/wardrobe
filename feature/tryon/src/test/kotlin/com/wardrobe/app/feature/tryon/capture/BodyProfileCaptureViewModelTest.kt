package com.wardrobe.app.feature.tryon.capture

import app.cash.turbine.test
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.feature.tryon.fakes.FakeBodyProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BodyProfileCaptureViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts on the first pose in BodyPose declaration order`() =
        runTest {
            val viewModel = BodyProfileCaptureViewModel(FakeBodyProfileRepository())

            assertEquals(BodyPose.NEUTRAL, viewModel.uiState.value.currentPose)
        }

    @Test
    fun `capturing a photo saves it and advances to the next pose`() =
        runTest {
            val repository = FakeBodyProfileRepository()
            val viewModel = BodyProfileCaptureViewModel(repository)

            viewModel.onPhotoCaptured("/tmp/neutral.jpg")

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.currentPose != BodyPose.ARMS_OUT) state = awaitItem()
                assertEquals(setOf(BodyPose.NEUTRAL), state.capturedPoses)
            }
            assertEquals(listOf(BodyPose.NEUTRAL to "/tmp/neutral.jpg"), repository.capturedPhotos)
        }

    @Test
    fun `after the last pose, measurements are recomputed exactly once and capture completes`() =
        runTest {
            val repository = FakeBodyProfileRepository()
            val viewModel = BodyProfileCaptureViewModel(repository)

            viewModel.uiState.test {
                var state = awaitItem()
                // Real usage only ever captures the *currently displayed* pose, one at a
                // time — each `onPhotoCaptured` call here waits for that pose's own
                // `currentPose` transition to land before triggering the next, the same
                // sequencing the guided-capture screen's reactive UI enforces.
                BodyPose.entries.forEach { pose ->
                    assertEquals(pose, state.currentPose)
                    viewModel.onPhotoCaptured("/tmp/${pose.name}.jpg")
                    state = awaitItem()
                    while (state.isSaving) state = awaitItem()
                }
                assertTrue(state.isComplete)
                assertTrue(state.currentPose == null)
                assertEquals(BodyPose.entries.toSet(), state.capturedPoses)
            }
            assertEquals(1, repository.recomputeMeasurementsCallCount)
        }
}
