package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
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
private val FIT_VALUES = Fit.entries.joinToString(", ")
private val LENGTH_VALUES = GarmentLength.entries.joinToString(", ")
private val SLEEVE_LENGTH_VALUES = SleeveLength.entries.joinToString(", ")
private val SEASON_VALUES = Season.entries.joinToString(", ")
private val DRESS_CODE_VALUES = DressCode.entries.joinToString(", ")

/** M25 real-device finding: `CATEGORY`/`SUBCATEGORY`/`PRIMARY_COLOR`/
 * `SECONDARY_COLOR`/`MATERIAL`/`FABRIC`/`OCCASION`/`STYLE_TAG` are all
 * resolved against this app's real, user-visible reference-data tables
 * (`MetadataSuggestionResolver`/`MetadataSuggestionApply`, `feature:capture`) —
 * without knowing what's actually in them, a cloud model's genuinely
 * correct-sounding free text ("Dress", "Yellow", "Tube Dress") routinely
 * fails to exact-match a differently-worded real row ("Dresses", a specific
 * seeded shade, no such subcategory at all), surfacing as an honestly-
 * reported but frustrating "Detected, but no matching option found" on
 * every one of those fields. Deliberately excludes [MetadataField.BRAND]:
 * unlike the others, an unseen real-world brand is a completely normal,
 * *correct* answer — constraining it to already-known brands would make
 * the model unable to ever report a brand the wardrobe hasn't seen yet.
 */
internal data class MetadataReferenceOptions(
    val categoryNames: List<String>,
    val colorNames: List<String>,
    val materialNames: List<String>,
    val fabricNames: List<String>,
    val occasionNames: List<String>,
    val tagNames: List<String>,
)

/** Instructs the cloud vision model to return exactly the shape
 * [parseMetadataSuggestions] parses — every valid [MetadataField] name is
 * listed explicitly so the model never invents a field this app doesn't
 * model, and the prompt is explicit that a field should be omitted
 * entirely rather than guessed. [parseSingleSuggestion] needs no changes
 * for the fields this prompt adds (`FABRIC`, `NECKLINE`, `GENDER`,
 * `WATERPROOF_LEVEL`, `OCCASION`) — it already resolves any field name via
 * `MetadataField.valueOf`, generically. */
internal fun buildMetadataSystemPrompt(referenceOptions: MetadataReferenceOptions): String {
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
        Valid FIT values: $FIT_VALUES.
        Valid LENGTH values: $LENGTH_VALUES.
        Valid SLEEVE_LENGTH values: $SLEEVE_LENGTH_VALUES.
        Valid SEASON values: $SEASON_VALUES.
        Valid DRESS_CODE values: $DRESS_CODE_VALUES.
        OCCASION is a specific real-world setting the item suits (e.g. "Work",
        "Formal", "Athletic") — distinct from DRESS_CODE, which is the
        general formality level; report both independently.
        ${referenceOptionsSection(referenceOptions)}
        """.trimIndent()
}

/** Empty for a brand-new wardrobe with nothing seeded yet in a given table —
 * omitted entirely rather than instructing the model to "choose from: "
 * (an empty, nonsensical constraint). */
private fun referenceOptionsSection(options: MetadataReferenceOptions): String {
    val lines =
        buildList {
            optionLine("CATEGORY and SUBCATEGORY", options.categoryNames)?.let(::add)
            optionLine("PRIMARY_COLOR and SECONDARY_COLOR", options.colorNames)?.let(::add)
            optionLine("MATERIAL", options.materialNames)?.let(::add)
            optionLine("FABRIC", options.fabricNames)?.let(::add)
            optionLine("OCCASION", options.occasionNames)?.let(::add)
            optionLine("STYLE_TAG", options.tagNames)?.let(::add)
        }
    if (lines.isEmpty()) return ""
    return "\n" +
        "This wardrobe app already has a real, fixed list of options for some " +
        "fields below — you MUST choose the single closest match from the " +
        "given list for these fields, never a different wording, even if your " +
        "own phrasing feels more precise. If truly nothing on the list is a " +
        "reasonable match, omit that field entirely rather than inventing a " +
        "new value.\n" + lines.joinToString("\n")
}

private fun optionLine(
    label: String,
    names: List<String>,
): String? = names.takeIf { it.isNotEmpty() }?.let { "Valid $label options: ${it.joinToString(", ")}." }

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
