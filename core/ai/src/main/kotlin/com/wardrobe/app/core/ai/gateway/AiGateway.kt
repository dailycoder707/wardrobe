package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiResultProvenance

/** Bundles the three things every Gateway call needs about *who's asking*
 * — kept as one parameter rather than three, both for readability and to
 * stay under this project's `LongParameterList` threshold. */
data class AiDispatchContext(
    val capability: AiCapability,
    val config: AiProviderConfig,
    val apiKey: String,
)

/**
 * The single, capability-agnostic choke point for every cloud call
 * (ADR-012 §1) — a per-capability Router (`core:data`, M7) is the only
 * caller, and only on the cloud path; the on-device path never touches
 * this. Internally, every dispatch runs privacy preprocessing → cache
 * lookup → job-managed dispatch → provider adapter → provenance/cost
 * logging, in that order, regardless of which of the two entry points is
 * used.
 *
 * Two entry points cover every capability's cloud need rather than one
 * maximally-generic payload type: [runVisionPrompt] for "reason over an
 * image/context, return structured text" (Metadata, Styling), and
 * [runImageTask] for "image(s) in, image out" (Extraction, Reconstruction,
 * Try-On).
 */
interface AiGateway {
    suspend fun runVisionPrompt(
        context: AiDispatchContext,
        promptVersion: String,
        systemPrompt: String,
        userPrompt: String,
        image: Bitmap,
        /** M24 — see [VisionPromptAdapterRequest.expectJsonResponse]'s KDoc;
         * threaded straight through, `false` (prompt-only) by default so
         * every pre-existing caller (e.g. the connection-test ping) is
         * unaffected. */
        expectJsonResponse: Boolean = false,
    ): VisionPromptResult

    suspend fun runImageTask(
        context: AiDispatchContext,
        promptVersion: String,
        taskType: String,
        images: List<Bitmap>,
    ): ImageTaskResult
}

sealed interface VisionPromptResult {
    data class Success(
        val rawResponseText: String,
        val provenance: AiResultProvenance,
    ) : VisionPromptResult

    data class Failure(
        val reason: String,
    ) : VisionPromptResult
}

sealed interface ImageTaskResult {
    data class Success(
        val resultImage: Bitmap,
        val confidence: Float?,
        val provenance: AiResultProvenance,
    ) : ImageTaskResult

    data class Failure(
        val reason: String,
    ) : ImageTaskResult
}
