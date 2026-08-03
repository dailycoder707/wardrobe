package com.wardrobe.app.core.network.weather

import com.wardrobe.app.core.model.weather.WeatherCondition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherProviderTest {
    @Test
    fun `wmoCodeToCondition maps every known code family correctly`() {
        assertEquals(WeatherCondition.SUNNY, wmoCodeToCondition(0))
        assertEquals(WeatherCondition.CLOUDY, wmoCodeToCondition(2))
        assertEquals(WeatherCondition.FOG, wmoCodeToCondition(45))
        assertEquals(WeatherCondition.RAIN, wmoCodeToCondition(61))
        assertEquals(WeatherCondition.RAIN, wmoCodeToCondition(80))
        assertEquals(WeatherCondition.SNOW, wmoCodeToCondition(73))
        assertEquals(WeatherCondition.STORM, wmoCodeToCondition(95))
        assertEquals(WeatherCondition.UNKNOWN, wmoCodeToCondition(null))
        assertEquals(WeatherCondition.UNKNOWN, wmoCodeToCondition(12345))
    }

    @Test
    fun `fetchForecast maps today's current block onto index zero only`() =
        runTest {
            val service =
                FakeOpenMeteoService(
                    OpenMeteoResponseDto(
                        current =
                            OpenMeteoCurrentDto(
                                temperature2m = 22.0,
                                apparentTemperature = 20.0,
                                relativeHumidity2m = 55,
                                weatherCode = 61,
                                windSpeed10m = 10.0,
                            ),
                        daily =
                            OpenMeteoDailyDto(
                                time = listOf("2026-08-02", "2026-08-03"),
                                temperature2mMax = listOf(24.0, 26.0),
                                temperature2mMin = listOf(18.0, 19.0),
                                apparentTemperatureMax = listOf(23.0, 25.0),
                                apparentTemperatureMin = listOf(17.0, 18.0),
                                precipitationProbabilityMax = listOf(80, 10),
                                uvIndexMax = listOf(5.0, 6.0),
                                weatherCode = listOf(61, 1),
                                windSpeed10mMax = listOf(12.0, 8.0),
                            ),
                    ),
                )
            val provider = OpenMeteoWeatherProvider(service)

            val observations = provider.fetchForecast(51.5, -0.1)

            assertEquals(2, observations.size)
            val today = observations[0]
            assertEquals(22.0, today.currentTempC)
            assertEquals(WeatherCondition.RAIN, today.condition)
            val tomorrow = observations[1]
            assertNull(tomorrow.currentTempC)
            assertEquals(WeatherCondition.CLOUDY, tomorrow.condition)
            assertTrue(tomorrow.tempHighC == 26.0)
        }

    @Test
    fun `fetchForecast returns empty list when daily block is missing`() =
        runTest {
            val provider = OpenMeteoWeatherProvider(FakeOpenMeteoService(OpenMeteoResponseDto()))
            assertEquals(emptyList<WeatherObservation>(), provider.fetchForecast(0.0, 0.0))
        }

    private class FakeOpenMeteoService(
        private val response: OpenMeteoResponseDto,
    ) : OpenMeteoService {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            daily: String,
            timezone: String,
            forecastDays: Int,
        ): OpenMeteoResponseDto = response
    }
}
