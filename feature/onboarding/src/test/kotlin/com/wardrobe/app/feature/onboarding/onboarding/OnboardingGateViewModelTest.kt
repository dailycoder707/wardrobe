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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingGateViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a new install (not complete) resolves to false, routing to onboarding`() =
        runTest {
            val viewModel = OnboardingGateViewModel(FakeOnboardingRepository(initiallyComplete = false))

            viewModel.isOnboardingComplete.test {
                assertEquals(null, awaitItem())
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `an already-onboarded device resolves to true, routing straight to Home`() =
        runTest {
            val viewModel = OnboardingGateViewModel(FakeOnboardingRepository(initiallyComplete = true))

            viewModel.isOnboardingComplete.test {
                assertEquals(null, awaitItem())
                assertEquals(true, awaitItem())
            }
        }
}
