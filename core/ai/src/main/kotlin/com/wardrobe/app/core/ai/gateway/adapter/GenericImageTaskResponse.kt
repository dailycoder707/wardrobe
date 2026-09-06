package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.Serializable

/**
 * The documented contract any self-hosted or third-party image-task
 * backend must implement (ADR-012) — there is no industry-standard shape
 * for "extract/reconstruct/render a garment image" the way chat-vision has
 * one, so this project defines its own minimal one rather than assuming a
 * specific vendor's API.
 */
@Serializable
data class GenericImageTaskResponse(
    val resultImageBase64: String? = null,
    val confidence: Float? = null,
    val error: String? = null,
)
