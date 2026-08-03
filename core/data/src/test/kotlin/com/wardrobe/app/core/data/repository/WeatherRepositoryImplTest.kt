package com.wardrobe.app.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.data.repository.weather.DeviceLocationSource
import com.wardrobe.app.core.data.repository.weather.WeatherLocationResolver
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.WeatherPreferences
import com.wardrobe.app.core.network.weather.WeatherObservation
import com.wardrobe.app.core.network.weather.WeatherProvider
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY = LocalDate.of(2026, 8, 2)
private val FIXED_INSTANT = Instant.parse("2026-08-02T12:00:00Z")
private val LOCATION = Location(latitude = 51.5074, longitude = -0.1278)

private class FakeWeatherProvider(
    var observations: List<WeatherObservation> = emptyList(),
    var throwOnFetch: Boolean = false,
) : WeatherProvider {
    override suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
    ): List<WeatherObservation> {
        if (throwOnFetch) error("simulated network failure")
        return observations
    }
}

private class FakeWeatherPreferencesRepository(
    initial: WeatherPreferences = WeatherPreferences(),
) : WeatherPreferencesRepository {
    private val flow = MutableStateFlow(initial)

    override fun observePreferences(): Flow<WeatherPreferences> = flow.asStateFlow()

    override suspend fun setPreferences(preferences: WeatherPreferences) {
        flow.value = preferences
    }
}

private fun observation(date: LocalDate) =
    WeatherObservation(
        date = date,
        currentTempC = 22.0,
        feelsLikeC = 20.0,
        humidityPercent = 55,
        windSpeedKph = 10.0,
        uvIndex = 5.0,
        precipitationProbabilityPercent = 20,
        condition = WeatherCondition.SUNNY,
        conditionCode = "0",
        tempHighC = 24.0,
        tempLowC = 18.0,
        apparentTempHighC = 23.0,
        apparentTempLowC = 17.0,
    )

/**
 * "Weather repository tests" + "Offline fallback tests" (Phase 7 brief) —
 * verifies the exact resilience contract phase-1-architecture.md Section 18
 * describes: live fetch when available, the cached row for the exact date
 * when the fetch fails, the most recent cached row regardless of date when
 * even that's missing, and a fully-empty stale snapshot when nothing was
 * ever cached — this class never throws in any of these cases.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var provider: FakeWeatherProvider
    private lateinit var preferences: FakeWeatherPreferencesRepository
    private val clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(ApplicationProvider.getApplicationContext())
        provider = FakeWeatherProvider()
        preferences = FakeWeatherPreferencesRepository()
    }

    private fun repository(): WeatherRepositoryImpl {
        val locationResolver =
            WeatherLocationResolver(DeviceLocationSource(ApplicationProvider.getApplicationContext()), preferences)
        return WeatherRepositoryImpl(provider, db.weatherCacheDao(), preferences, locationResolver, clock)
    }

    @Test
    fun `getForecast returns a fresh non-stale snapshot on a successful fetch`() =
        runTest {
            provider.observations = listOf(observation(TODAY))

            val snapshot = repository().getForecast(LOCATION, TODAY)

            assertFalse(snapshot.isStale)
            assertEquals(22.0, snapshot.currentTempC)
            assertEquals(WeatherCondition.SUNNY, snapshot.condition)
        }

    @Test
    fun `getForecast falls back to the exact-date cache row when the live fetch fails`() =
        runTest {
            provider.observations = listOf(observation(TODAY))
            repository().getForecast(LOCATION, TODAY)

            provider.throwOnFetch = true
            val snapshot = repository().getForecast(LOCATION, TODAY)

            assertTrue(snapshot.isStale)
            assertEquals(22.0, snapshot.currentTempC)
        }

    @Test
    fun `getForecast falls back to the most recent cache row when nothing exists for the exact date`() =
        runTest {
            val yesterday = TODAY.minusDays(1)
            provider.observations = listOf(observation(yesterday))
            repository().getForecast(LOCATION, yesterday)

            provider.throwOnFetch = true
            val snapshot = repository().getForecast(LOCATION, TODAY)

            assertTrue(snapshot.isStale)
            assertEquals(22.0, snapshot.currentTempC)
        }

    @Test
    fun `getForecast returns a fully-empty stale snapshot when nothing was ever cached`() =
        runTest {
            provider.throwOnFetch = true

            val snapshot = repository().getForecast(LOCATION, TODAY)

            assertTrue(snapshot.isStale)
            assertNull(snapshot.currentTempC)
            assertNull(snapshot.tempHighC)
        }

    @Test
    fun `getForecast never attempts a live fetch when offlineOnly is set`() =
        runTest {
            provider.observations = listOf(observation(TODAY))
            repository().getForecast(LOCATION, TODAY)
            preferences.setPreferences(WeatherPreferences(offlineOnly = true))
            provider.observations = listOf(observation(TODAY).copy(currentTempC = 99.0))

            val snapshot = repository().getForecast(LOCATION, TODAY)

            assertTrue(snapshot.isStale)
            assertEquals(22.0, snapshot.currentTempC)
        }

    @Test
    fun `getForecastForConfiguredLocation returns null when useWeather is off`() =
        runTest {
            preferences.setPreferences(WeatherPreferences(useWeather = false))

            val snapshot = repository().getForecastForConfiguredLocation(TODAY)

            assertNull(snapshot)
        }
}
