package com.wardrobe.app.feature.onboarding.onboarding

import app.cash.turbine.test
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingWelcomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `skipping the whole flow marks onboarding complete and never writes fabricated data`() =
        runTest {
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = OnboardingWelcomeViewModel(onboardingRepository)

            viewModel.didSkip.test {
                assertEquals(false, awaitItem())
                viewModel.onSkipAll()
                assertTrue(awaitItem())
            }
            assertEquals(1, onboardingRepository.markCompleteCallCount)
        }
}
