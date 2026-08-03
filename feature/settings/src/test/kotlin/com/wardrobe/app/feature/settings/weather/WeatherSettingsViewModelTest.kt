package com.wardrobe.app.feature.settings.weather

import app.cash.turbine.test
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSettingsViewModelTest {
    private lateinit var repository: FakeWeatherPreferencesRepository
    private lateinit var scheduler: FakeWeatherRefreshScheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeWeatherPreferencesRepository()
        scheduler = FakeWeatherRefreshScheduler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults use weather with device location and 3-hour refresh`() =
        runTest {
            val viewModel = WeatherSettingsViewModel(repository, scheduler)
            viewModel.preferences.test {
                val prefs = awaitItem()
                assertTrue(prefs.useWeather)
                assertTrue(prefs.useDeviceLocation)
                assertEquals(WeatherPreferences.DEFAULT_REFRESH_INTERVAL_HOURS, prefs.refreshIntervalHours)
            }
        }

    @Test
    fun `update persists a transformed copy and reschedules the refresh worker`() =
        runTest {
            val viewModel = WeatherSettingsViewModel(repository, scheduler)
            viewModel.update { it.copy(refreshIntervalHours = 6, temperatureUnit = TemperatureUnit.FAHRENHEIT) }
            testScheduler.advanceUntilIdle()

            assertEquals(6, repository.flow.value.refreshIntervalHours)
            assertEquals(TemperatureUnit.FAHRENHEIT, repository.flow.value.temperatureUnit)
            assertEquals(6, scheduler.lastRescheduledIntervalHours)
        }

    @Test
    fun `turning off useWeather leaves other preferences untouched`() =
        runTest {
            repository.flow.value = WeatherPreferences(offlineOnly = true)
            val viewModel = WeatherSettingsViewModel(repository, scheduler)
            // `update` reads `preferences.value` — a `stateIn`-backed
            // StateFlow only reflects the repository's real current value
            // once something has subscribed (`WhileSubscribed` never starts
            // collecting otherwise), so the subscription and the update must
            // both happen inside the same live collection.
            viewModel.preferences.test {
                awaitItem() // the stateIn seed default, before upstream ever emits
                awaitItem() // the repository's real current value now that upstream ran
                viewModel.update { it.copy(useWeather = false) }
                awaitItem() // the post-update value flowing back through the repository
            }

            assertFalse(repository.flow.value.useWeather)
            assertTrue(repository.flow.value.offlineOnly)
        }
}
