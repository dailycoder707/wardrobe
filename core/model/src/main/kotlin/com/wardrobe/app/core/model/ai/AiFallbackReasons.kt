package com.wardrobe.app.core.model.ai

/**
 * Well-known [AiResultProvenance.fallbackReason] values that call sites above
 * the AI gateway need to recognize by name, distinct from free-text failure
 * reasons (a timeout, a provider's own error message) which are never listed
 * here. Each entry is a structural fact about how the app/provider is put
 * together, not a transient failure — the UI uses that distinction to avoid
 * saying "something went wrong" about a case that's actually "this provider
 * was never going to be able to do this."
 */
object AiFallbackReasons {
    /** Set by [com.wardrobe.app.core.ai.gateway.DefaultAiGateway.runImageTask]
     * when the configured vendor has no `ImageTaskAdapter` binding at all —
     * true for every vision-language provider (Gemini, OpenAI, …), which
     * exposes image-understanding/generation but no pixel-accurate
     * segmentation mask a garment cutout can be built from. Fixed for as
     * long as that vendor has no such adapter registered; never transient. */
    const val IMAGE_TASK_ADAPTER_UNAVAILABLE = "image_task_adapter_unavailable"

    /** Set by [com.wardrobe.app.core.data.ai.GarmentExtractionEngineRouter]
     * when a Gemini segmentation dispatch genuinely succeeded at the HTTP
     * level but its response couldn't be turned into a usable cutout — no
     * detection returned, a malformed/undecodable mask, or a degenerate
     * bounding box. Distinct from [IMAGE_TASK_ADAPTER_UNAVAILABLE]: the
     * capability was genuinely attempted here, so this describes the
     * outcome of that attempt, not a fixed fact about the provider. */
    const val GEMINI_SEGMENTATION_UNUSABLE = "gemini_segmentation_response_unusable"
}
