package com.wardrobe.app.core.data.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.wardrobe.app.core.network.weather.OpenMeteoService
import com.wardrobe.app.core.network.weather.OpenMeteoWeatherProvider
import com.wardrobe.app.core.network.weather.WeatherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                // M22 fix — release builds have no reason to log request
                // URLs/response codes at all (production hardening); see
                // `AiNetworkModule`'s identical fix for the fuller rationale.
                if (isDebugBuild(context)) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }.build()

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

private fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
