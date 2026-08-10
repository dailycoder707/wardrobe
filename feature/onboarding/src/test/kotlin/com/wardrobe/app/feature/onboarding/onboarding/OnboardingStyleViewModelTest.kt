package com.wardrobe.app.feature.onboarding.onboarding

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.styling.RecommendationPreferences
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

/** [OnboardingStyleViewModel.preferences] is `stateIn`-seeded with a default
 * [RecommendationPreferences] before the real (already-hot) fake
 * repository's own value arrives as a second, distinct emission — the same
 * gotcha `AiProvidersViewModelTest`/`WeatherSettingsViewModelTest` already
 * document for their own `stateIn`-backed state. */
private suspend fun ReceiveTurbine<RecommendationPreferences>.awaitRealInitialValue(): RecommendationPreferences {
    awaitItem()
    return awaitItem()
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingStyleViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preferences reflect the existing StylistPreferencesRepository, not a second model`() =
        runTest {
            val repository =
                FakeStylistPreferencesRepository(
                    RecommendationPreferences(preferredDressCodes = setOf(DressCode.CASUAL)),
                )
            val viewModel = OnboardingStyleViewModel(repository)

            viewModel.preferences.test {
                assertEquals(setOf(DressCode.CASUAL), awaitRealInitialValue().preferredDressCodes)
            }
        }

    @Test
    fun `updating a preference persists through the real repository and survives reload`() =
        runTest {
            val repository = FakeStylistPreferencesRepository()
            val viewModel = OnboardingStyleViewModel(repository)

            viewModel.preferences.test {
                // The ViewModel's own `stateIn` seed and the fake repository's
                // default initial value are both `RecommendationPreferences()`
                // — equal, so `stateIn` never emits a second, distinct item
                // for the initial subscription (only `awaitItem()` once).
                awaitItem()
                viewModel.update { it.copy(preferredDressCodes = setOf(DressCode.FORMAL)) }
                assertEquals(setOf(DressCode.FORMAL), awaitItem().preferredDressCodes)
            }

            val reloaded = OnboardingStyleViewModel(repository)
            reloaded.preferences.test {
                assertEquals(setOf(DressCode.FORMAL), awaitRealInitialValue().preferredDressCodes)
            }
        }

    @Test
    fun `never calling update preserves the existing defaults untouched`() =
        runTest {
            val repository = FakeStylistPreferencesRepository()
            OnboardingStyleViewModel(repository)

            assertEquals(RecommendationPreferences(), repository.flow.value)
            assertTrue(repository.flow.value.preferFavorites)
        }
}
