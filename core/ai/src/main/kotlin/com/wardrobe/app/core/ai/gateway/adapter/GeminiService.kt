package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiService {
    @POST
    @Headers("Content-Type: application/json")
    suspend fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: JsonObject,
    ): GeminiGenerateContentResponse

    /** Same auth transport as [generateContent] — the header is rewritten to
     * `?key=` by [GeminiQueryParamAuthInterceptor] for this call too. */
    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
    ): GeminiModelsResponse
}
