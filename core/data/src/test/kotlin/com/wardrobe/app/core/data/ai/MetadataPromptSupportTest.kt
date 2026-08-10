package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val PROVENANCE = AiResultProvenance(AiResultSource.CLOUD, "generic", null, "metadata-v1", Instant.EPOCH)

private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")

class MetadataPromptSupportTest {
    @Test
    fun `system prompt lists every MetadataField by name, including the newly-bound ones`() {
        val prompt = buildMetadataSystemPrompt()

        listOf(
            MetadataField.FABRIC,
            MetadataField.SUBCATEGORY,
            MetadataField.SECONDARY_COLOR,
            MetadataField.NECKLINE,
            MetadataField.GENDER,
            MetadataField.WATERPROOF_LEVEL,
            MetadataField.OCCASION,
            MetadataField.STYLE_TAG,
        ).forEach { field -> assertTrue("prompt should mention ${field.name}", field.name in prompt) }
    }

    @Test
    fun `system prompt explains FABRIC is distinct from MATERIAL`() {
        val normalized = buildMetadataSystemPrompt().normalizeWhitespace()

        assertTrue("distinct from MATERIAL" in normalized)
    }

    @Test
    fun `system prompt explains OCCASION is distinct from DRESS_CODE`() {
        val normalized = buildMetadataSystemPrompt().normalizeWhitespace()

        assertTrue("distinct from DRESS_CODE" in normalized)
    }

    @Test
    fun `system prompt spells out valid NECKLINE, GENDER and WATERPROOF_LEVEL enum values`() {
        val prompt = buildMetadataSystemPrompt()

        assertTrue("CREW" in prompt)
        assertTrue("UNISEX" in prompt)
        assertTrue("WATER_RESISTANT" in prompt)
    }

    @Test
    fun `system prompt allows OCCASION to repeat, alongside SEASON, DRESS_CODE and STYLE_TAG`() {
        val prompt = buildMetadataSystemPrompt()

        assertTrue("SEASON, DRESS_CODE, OCCASION, and STYLE_TAG may each appear more than" in prompt)
    }

    @Test
    fun `system prompt tells the model to omit a field entirely rather than guess N slash A`() {
        val prompt = buildMetadataSystemPrompt()

        assertTrue("never a" in prompt && "placeholder value" in prompt)
    }

    @Test
    fun `parses every valid suggestion in a well-formed response`() {
        val raw =
            """
            {"suggestions": [
                {"field": "PRIMARY_COLOR", "value": "navy", "confidence": 0.9},
                {"field": "PATTERN", "value": "solid", "confidence": 0.6}
            ]}
            """.trimIndent()

        val result = parseMetadataSuggestions(raw, PROVENANCE)

        assertEquals(2, result.size)
        assertEquals(MetadataField.PRIMARY_COLOR, result[0].field)
        assertEquals(MetadataField.PATTERN, result[1].field)
    }

    @Test
    fun `drops an entry with an unknown field name rather than crashing`() {
        val raw = """{"suggestions": [{"field": "NOT_A_REAL_FIELD", "value": "x", "confidence": 0.9}]}"""

        val result = parseMetadataSuggestions(raw, PROVENANCE)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `keeps the suggestion but nulls out an out-of-range confidence rather than clamping it`() {
        val raw = """{"suggestions": [{"field": "PRIMARY_COLOR", "value": "navy", "confidence": 1.7}]}"""

        val result = parseMetadataSuggestions(raw, PROVENANCE)

        assertEquals(1, result.size)
        assertEquals(null, result.first().confidence)
    }

    @Test
    fun `drops an entry with a blank value`() {
        val raw = """{"suggestions": [{"field": "PRIMARY_COLOR", "value": "", "confidence": 0.9}]}"""

        val result = parseMetadataSuggestions(raw, PROVENANCE)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `keeps an entry with no confidence at all rather than fabricating one`() {
        val raw = """{"suggestions": [{"field": "PRIMARY_COLOR", "value": "navy"}]}"""

        val result = parseMetadataSuggestions(raw, PROVENANCE)

        assertEquals(1, result.size)
        assertEquals(null, result.first().confidence)
    }

    @Test
    fun `returns an empty list for malformed JSON rather than throwing`() {
        val result = parseMetadataSuggestions("not json at all {{{", PROVENANCE)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns an empty list when the suggestions key is missing`() {
        val result = parseMetadataSuggestions("""{"unexpected": true}""", PROVENANCE)

        assertTrue(result.isEmpty())
    }
}
