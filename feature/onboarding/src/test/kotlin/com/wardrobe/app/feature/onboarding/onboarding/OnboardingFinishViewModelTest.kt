package com.wardrobe.app.feature.onboarding.onboarding

import app.cash.turbine.test
import com.wardrobe.app.core.model.profile.PersonalizationSettings
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

/** [OnboardingFinishViewModel.displayName] is `stateIn`-seeded with `null` —
 * a genuinely unset name settles to that same `null`, so no second,
 * distinct emission ever fires (`expectNoEvents()` below is the correct
 * assertion, not a second `awaitItem()`); a real saved name always differs
 * from the seed and does produce one. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFinishViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `displayName reflects a real saved name`() =
        runTest {
            val personalization =
                FakePersonalizationRepository(PersonalizationSettings.DEFAULT.copy(displayName = "Alex"))
            val viewModel = OnboardingFinishViewModel(personalization, FakeOnboardingRepository())

            viewModel.displayName.test {
                awaitItem()
                assertEquals("Alex", awaitItem())
            }
        }

    @Test
    fun `displayName is null, never fabricated, when no name was ever saved`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingFinishViewModel(personalization, FakeOnboardingRepository())

            viewModel.displayName.test {
                assertEquals(null, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `finishing marks onboarding complete`() =
        runTest {
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = OnboardingFinishViewModel(FakePersonalizationRepository(), onboardingRepository)

            viewModel.didFinish.test {
                assertEquals(false, awaitItem())
                viewModel.onFinish()
                assertTrue(awaitItem())
            }
            assertEquals(1, onboardingRepository.markCompleteCallCount)
        }
}
