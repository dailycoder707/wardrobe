package com.wardrobe.app.feature.outfits.preferences

import app.cash.turbine.test
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.feature.outfits.fakes.FakeStylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StylistPreferencesViewModelTest {
    private lateinit var repository: FakeStylistPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeStylistPreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default preferences include shoes, bags, jewelry, and watch`() =
        runTest {
            val viewModel = StylistPreferencesViewModel(repository)
            viewModel.preferences.test {
                val prefs = awaitItem()
                assertTrue(prefs.includeShoes)
                assertTrue(prefs.includeBags)
                assertTrue(prefs.includeJewelry)
                assertTrue(prefs.includeWatch)
            }
        }

    @Test
    fun `update persists a transformed copy through the repository`() =
        runTest {
            val viewModel = StylistPreferencesViewModel(repository)
            viewModel.update { it.copy(includeSunglasses = true) }
            testScheduler.advanceUntilIdle()

            assertTrue(repository.flow.value.includeSunglasses)
        }

    @Test
    fun `disabling a category leaves every other preference untouched`() =
        runTest {
            repository.flow.value = RecommendationPreferences(includeBelt = true)
            val viewModel = StylistPreferencesViewModel(repository)
            viewModel.update { it.copy(includeBelt = false) }
            testScheduler.advanceUntilIdle()

            assertFalse(repository.flow.value.includeBelt)
            assertTrue(repository.flow.value.includeShoes)
        }
}
