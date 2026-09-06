package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/** One Retrofit interface serves every OpenAI-compatible vendor — the
 * [Url] is absolute (this project's shared Retrofit instance is built with
 * a placeholder base URL, never used), and [HeaderMap] carries whichever
 * auth scheme that vendor needs, so vendor differences never leak past the
 * adapter that calls this. */
interface OpenAiCompatibleService {
    @POST
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: JsonObject,
    ): ChatCompletionsResponse
}
