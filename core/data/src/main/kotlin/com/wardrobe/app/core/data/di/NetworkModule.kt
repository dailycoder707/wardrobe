package com.wardrobe.app.core.data.di

import com.wardrobe.app.core.network.weather.OpenMeteoService
import com.wardrobe.app.core.network.weather.OpenMeteoWeatherProvider
import com.wardrobe.app.core.network.weather.WeatherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import javax.inject.Singleton

/**
 * Open-Meteo only — the single outbound network call this app makes
 * (phase-1-architecture.md Section 18, `core:network`'s own build-file
 * comment). No auth/interceptor beyond logging: Open-Meteo's free tier needs
 * no API key.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(OpenMeteoService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoService(retrofit: Retrofit): OpenMeteoService = retrofit.create()

    @Provides
    @Singleton
    fun provideWeatherProvider(service: OpenMeteoService): WeatherProvider = OpenMeteoWeatherProvider(service)
}
