package com.wardrobe.app.core.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M24 real-device finding — on a physical tablet, selecting `GEMINI` for
 * Garment Metadata and granting consent still left cloud unreachable
 * because the Base URL field was never filled in and nothing prompted for
 * it. [AiVendor.defaultBaseUrl] is the fix's data half: proves every vendor
 * with one real, fixed public API root has it declared, and that the three
 * vendors with no safe universal default (a user-specific or self-hosted
 * endpoint) are never guessed.
 */
class AiVendorTest {
    @Test
    fun `vendors with one real fixed public API root have a declared default`() {
        assertEquals("https://api.openai.com", AiVendor.OPENAI.defaultBaseUrl())
        assertEquals("https://generativelanguage.googleapis.com", AiVendor.GEMINI.defaultBaseUrl())
        assertEquals("https://api.anthropic.com", AiVendor.CLAUDE.defaultBaseUrl())
        assertEquals("https://openrouter.ai/api", AiVendor.OPENROUTER.defaultBaseUrl())
    }

    @Test
    fun `vendors with no single correct default are never guessed`() {
        assertNull(AiVendor.AZURE_OPENAI.defaultBaseUrl())
        assertNull(AiVendor.OLLAMA.defaultBaseUrl())
        assertNull(AiVendor.GENERIC_REST.defaultBaseUrl())
    }
}
