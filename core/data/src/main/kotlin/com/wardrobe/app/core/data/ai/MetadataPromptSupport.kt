package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.WaterproofLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val METADATA_JSON = Json { ignoreUnknownKeys = true }

private val NECKLINE_VALUES = Neckline.entries.joinToString(", ")
private val GENDER_VALUES = GarmentGender.entries.joinToString(", ")
private val WATERPROOF_VALUES = WaterproofLevel.entries.joinToString(", ")

/** Instructs the cloud vision model to return exactly the shape
 * [parseMetadataSuggestions] parses — every valid [MetadataField] name is
 * listed explicitly so the model never invents a field this app doesn't
 * model, and the prompt is explicit that a field should be omitted
 * entirely rather than guessed. [parseSingleSuggestion] needs no changes
 * for the fields this prompt adds (`FABRIC`, `NECKLINE`, `GENDER`,
 * `WATERPROOF_LEVEL`, `OCCASION`) — it already resolves any field name via
 * `MetadataField.valueOf`, generically. */
internal fun buildMetadataSystemPrompt(): String {
    val fieldNames = MetadataField.entries.joinToString(", ")
    return """
        You are analyzing a single clothing/fashion item photographed against a
        plain or transparent background. Identify only what you can determine
        with a genuine, reasoned basis from the image itself.

        Respond with ONLY a JSON object of exactly this shape, no other text:
        {"suggestions": [{"field": "<FIELD_NAME>", "value": "<short value>", "confidence": <0.0-1.0>}]}

        Valid field names (use these exact names, nothing else): $fieldNames.
        SEASON, DRESS_CODE, OCCASION, and STYLE_TAG may each appear more than
        once for multiple applicable values. Omit a field entirely if you are
        not reasonably confident about it, and omit it entirely (never a
        placeholder value like "N/A") if the field genuinely does not apply to
        this item's category — for example, a sock or a belt has no NECKLINE,
        a hat has no FIT. confidence must be your genuine estimate for that
        specific value, not a placeholder or a constant.

        FABRIC is the weave/construction (e.g. Denim, Jersey, Twill, Flannel,
        Fleece, Satin, Chiffon, Canvas, Corduroy, Oxford, Poplin) — distinct
        from MATERIAL, which is fiber content (e.g. Cotton, Wool, Polyester).
        Report both independently; do not assume they must match.

        Valid NECKLINE values: $NECKLINE_VALUES.
        Valid GENDER values: $GENDER_VALUES.
        Valid WATERPROOF_LEVEL values: $WATERPROOF_VALUES.
        OCCASION is a specific real-world setting the item suits (e.g. "Work",
        "Formal", "Athletic") — distinct from DRESS_CODE, which is the
        general formality level; report both independently.
        """.trimIndent()
}

internal const val METADATA_USER_PROMPT = "Analyze this garment and return the JSON described in your instructions."

/** Never crashes or fabricates on a malformed/unexpected response — an
 * unparseable body, a missing "suggestions" array, an unknown field name,
 * or an out-of-range confidence each simply drop that one entry
 * (Constitution rule 4), rather than the whole response failing loudly or
 * silently coercing bad data into a guess. */
internal fun parseMetadataSuggestions(
    rawResponseText: String,
    provenance: AiResultProvenance,
): List<MetadataSuggestion> {
    val root = runCatching { METADATA_JSON.parseToJsonElement(rawResponseText).jsonObject }.getOrNull()
    val suggestionsArray = root?.get("suggestions") as? JsonArray
    return suggestionsArray?.mapNotNull { element -> parseSingleSuggestion(element.jsonObject, provenance) }.orEmpty()
}

private fun parseSingleSuggestion(
    obj: JsonObject,
    provenance: AiResultProvenance,
): MetadataSuggestion? {
    val field =
        obj["field"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { name -> runCatching { MetadataField.valueOf(name) }.getOrNull() }
    val value = obj["value"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull?.takeIf { it in 0f..1f }
    return if (field != null && value != null) MetadataSuggestion(field, value, confidence, provenance) else null
}
