package com.wardrobe.app.core.ai.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.wardrobe.app.core.ai.gateway.adapter.ClaudeService
import com.wardrobe.app.core.ai.gateway.adapter.GeminiQueryParamAuthInterceptor
import com.wardrobe.app.core.ai.gateway.adapter.GeminiService
import com.wardrobe.app.core.ai.gateway.adapter.GenericRestService
import com.wardrobe.app.core.ai.gateway.adapter.OpenAiCompatibleService
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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** This app's second outbound network surface after `core:network`'s
 * weather-only client (ADR-012, `DEPENDENCIES.md`). One shared
 * Retrofit/OkHttp instance for every vendor adapter — every call uses an
 * absolute `@Url` (the configured `baseUrl` is never actually used, since
 * it must be non-blank for `Retrofit.Builder` regardless). Vision/image
 * generation calls can genuinely take longer than a JSON weather fetch, so
 * timeouts are longer than `core:network`'s. */
@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"

    @Provides
    @Singleton
    @AiHttp
    fun provideAiJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @AiHttp
    fun provideAiOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                // M22 fix (defense-in-depth, disclosed in TECHNICAL_DEBT.md's
                // RC2 entry as "not applied"): the real API-key leak this
                // logging could have caused was already fixed by switching
                // every adapter to header auth (RC2), but a release build
                // still had no reason to log request URLs/response codes at
                // all — production hardening closes that.
                if (isDebugBuild(context)) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
                // M24 real-device finding: Google's live API rejects the
                // RC2 header-auth approach for at least some Gemini API
                // keys/projects (confirmed independently of this app).
                // Registered as a NETWORK interceptor, not an application
                // interceptor — application interceptors (including
                // HttpLoggingInterceptor above) operate purely at the
                // logical-call level and never see what a network
                // interceptor does to the actual on-the-wire request;
                // ordering an application interceptor before this one is
                // NOT sufficient (HttpLoggingInterceptor's own response-side
                // log line reflects the request that was actually sent,
                // which a same-level *application* interceptor added after
                // it can still end up reflected in — verified the hard way
                // on a real device before this was corrected to a network
                // interceptor). A request with no Gemini auth header (every
                // other vendor) passes through this interceptor unchanged.
                addNetworkInterceptor(GeminiQueryParamAuthInterceptor())
            }.build()

    @Provides
    @Singleton
    @AiHttp
    fun provideAiRetrofit(
        @AiHttp client: OkHttpClient,
        @AiHttp json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleService(
        @AiHttp retrofit: Retrofit,
    ): OpenAiCompatibleService = retrofit.create()

    @Provides
    @Singleton
    fun provideGeminiService(
        @AiHttp retrofit: Retrofit,
    ): GeminiService = retrofit.create()

    @Provides
    @Singleton
    fun provideClaudeService(
        @AiHttp retrofit: Retrofit,
    ): ClaudeService = retrofit.create()

    @Provides
    @Singleton
    fun provideGenericRestService(
        @AiHttp retrofit: Retrofit,
    ): GenericRestService = retrofit.create()
}

/** No `BuildConfig.DEBUG` here — `core:ai` is a library module with no
 * `BuildConfig` of its own, and giving it one just for this single check
 * would be a bigger change than the flag Android already exposes on every
 * installed package. */
private fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
