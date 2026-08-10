package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** Mirrors `AiNetworkModule`'s real construction, pointed at a
 * [MockWebServer] instead of a placeholder base URL — every adapter under
 * test builds its own absolute `@Url` from `request.baseUrl`, so pointing
 * Retrofit's own configured base URL at the mock server too is enough for
 * `@Url`-based calls to actually reach it. */
internal inline fun <reified T> MockWebServer.retrofitService(): T {
    val json = Json { ignoreUnknownKeys = true }
    val retrofit =
        Retrofit
            .Builder()
            .baseUrl(url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    return retrofit.create()
}
