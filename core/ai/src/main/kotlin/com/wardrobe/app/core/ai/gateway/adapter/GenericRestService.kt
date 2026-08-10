package com.wardrobe.app.core.ai.gateway.adapter

import okhttp3.MultipartBody
import retrofit2.http.HeaderMap
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface GenericRestService {
    @Multipart
    @POST
    suspend fun runImageTask(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Part images: List<MultipartBody.Part>,
    ): GenericImageTaskResponse
}
