package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiFallbackReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentReviewMetadataScreenTest {
    @Test
    fun `a capability-absence fallback reads as a fixed provider limitation, not a failure`() {
        val message = extractionFallbackMessage(AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE)

        assertEquals(
            "Cloud AI doesn't support garment cutout generation for this provider — used On-Device AI instead.",
            message,
        )
        assertTrue(
            "should not echo the raw internal reason code",
            AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE !in message,
        )
    }

    @Test
    fun `a Gemini segmentation-unusable fallback reads as a real attempt that didn't pan out, not a raw reason code`() {
        val message = extractionFallbackMessage(AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE)

        assertEquals(
            "Gemini garment analysis succeeded, but it couldn't produce a usable cutout — used On-Device AI instead.",
            message,
        )
        assertTrue(
            "should not echo the raw internal reason code",
            AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE !in message,
        )
    }

    @Test
    fun `any other fallback reason is shown as a technical reason, distinct from capability absence`() {
        val message = extractionFallbackMessage("timeout")

        assertEquals(
            "Cloud AI unavailable for garment extraction — used On-Device AI instead. Reason: timeout",
            message,
        )
    }

    @Test
    fun `a provider-reported failure message is passed through, not replaced with a generic one`() {
        val reason = "model_not_found: models/x is no longer available"

        val message = extractionFallbackMessage(reason)

        assertTrue(reason in message)
    }
}
