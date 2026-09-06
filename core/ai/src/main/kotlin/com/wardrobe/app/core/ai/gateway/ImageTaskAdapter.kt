package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap

/**
 * The generic REST contract (ADR-012 §1) for image-in/image-out tasks
 * (Extraction/Reconstruction/Try-On) — unlike vision-prompt vendors, there's
 * no industry-standard wire shape for this, so [GENERIC_REST][com.wardrobe.app.core.model.ai.AiVendor.GENERIC_REST]
 * is the one adapter here: a documented multipart contract any self-hosted
 * or third-party backend the user points at must implement.
 */
interface ImageTaskAdapter {
    suspend fun run(request: ImageTaskAdapterRequest): ImageTaskAdapterResult
}

data class ImageTaskAdapterRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String?,
    val taskType: String,
    val images: List<Bitmap>,
)

sealed interface ImageTaskAdapterResult {
    data class Success(
        val resultImage: Bitmap,
        val confidence: Float?,
    ) : ImageTaskAdapterResult

    data class Failure(
        val reason: String,
    ) : ImageTaskAdapterResult
}
