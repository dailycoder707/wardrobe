package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface ClaudeService {
    @POST
    @Headers("Content-Type: application/json")
    suspend fun createMessage(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: JsonObject,
    ): ClaudeMessagesResponse
}
