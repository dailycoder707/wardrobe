package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.data.repository.styling.validateCloudOutfit
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val STYLING_JSON = Json { ignoreUnknownKeys = true }

/**
 * M12's cloud path for `OUTFIT_STYLING` — the only class that knows the
 * cloud styling response's wire shape. Every candidate outfit it produces
 * still passes through [validateCloudOutfit] (`OutfitAssembler.kt`) before
 * becoming a real [ScoredOutfit]: this class never trusts a provider's
 * `picks` map as-is, since a hallucinated or out-of-date garment ID would
 * otherwise reach the user as a "real" suggestion.
 */
@Singleton
class CloudStylingEngine
    @Inject
    constructor(
        private val aiGateway: AiGateway,
    ) {
        internal suspend fun suggestOutfits(
            context: SuggestionContext,
            input: EngineInput,
            dispatchContext: AiDispatchContext,
            inspirationImage: Bitmap?,
            count: Int,
            anchorGarmentId: GarmentId? = null,
        ): List<ScoredOutfit> {
            val image = inspirationImage ?: stylingContextFingerprintBitmap(input, context)
            val result =
                aiGateway.runVisionPrompt(
                    dispatchContext,
                    PromptVersions.STYLING_V1,
                    buildStylingSystemPrompt(input, context, anchorGarmentId),
                    STYLING_USER_PROMPT,
                    image,
                )
            return when (result) {
                is VisionPromptResult.Success -> {
                    parseAndValidate(result.rawResponseText, input, result.provenance, count, anchorGarmentId)
                }

                is VisionPromptResult.Failure -> {
                    emptyList()
                }
            }
        }

        private fun parseAndValidate(
            rawResponseText: String,
            input: EngineInput,
            provenance: AiResultProvenance,
            count: Int,
            anchorGarmentId: GarmentId?,
        ): List<ScoredOutfit> =
            parseCloudOutfitSuggestions(rawResponseText)
                .mapNotNull { suggestion ->
                    validateCloudOutfit(
                        picks = resolvePicks(suggestion.picks, input),
                        input = input,
                        reasoning = suggestion.reasoning,
                        confidence = suggestion.confidence,
                        provenance = provenance,
                        requiredAnchor = anchorGarmentId,
                    )
                }.take(count)

        private fun resolvePicks(
            picks: Map<String, Long>,
            input: EngineInput,
        ): Map<OutfitSlot, GarmentId> =
            picks
                .mapNotNull { (slotName, garmentIdValue) ->
                    val slot = runCatching { OutfitSlot.valueOf(slotName) }.getOrNull() ?: return@mapNotNull null
                    val garmentId = GarmentId(garmentIdValue)
                    if (garmentId !in input.garmentsById) return@mapNotNull null
                    slot to garmentId
                }.toMap()
    }

private const val STYLING_USER_PROMPT =
    "Suggest complete outfits from the wardrobe items listed in your instructions, " +
        "returning the JSON described there."

/** Every garment ID named in the prompt is real and currently in the
 * candidate pool — the model is told to choose only from this list, but
 * [validateCloudOutfit] never trusts that instruction alone (a cloud
 * response is untrusted input, not a guarantee). */
internal fun buildStylingSystemPrompt(
    input: EngineInput,
    context: SuggestionContext,
    anchorGarmentId: GarmentId? = null,
): String {
    val itemLines = input.candidateGarments.joinToString("\n") { describeGarmentForPrompt(it, input) }
    val weatherLine =
        input.weather?.let { "Apparent high ${it.apparentTempHighC}C, low ${it.apparentTempLowC}C." }
            ?: "No weather data available."
    val occasionLine = context.occasionId?.let { "Occasion id: ${it.value}." } ?: "No specific occasion."
    val slotNames = OutfitSlot.entries.joinToString(", ") { it.name }
    val anchorLine = anchorGarmentId?.let { "Every outfit's picks MUST include item id ${it.value}." }.orEmpty()
    return """
        You are a personal stylist choosing a complete outfit from the user's own
        wardrobe. Available items (id, slot, category, color id):
        $itemLines

        Weather: $weatherLine
        $occasionLine
        $anchorLine

        Respond with ONLY a JSON object of exactly this shape, no other text:
        {"outfits": [{"picks": {"<SLOT>": <item id>}, "reasoning": "<one sentence>", "confidence": <0.0-1.0>}]}

        Valid slot names (use these exact names as keys, nothing else): $slotNames.
        Every item id you use MUST be one of the ids listed above — never invent
        an id. Include either DRESS, or both TOP and BOTTOM, in every outfit's
        picks. confidence must be your genuine estimate, not a placeholder.
        """.trimIndent()
}

private fun describeGarmentForPrompt(
    garment: Garment,
    input: EngineInput,
): String {
    val slot = input.categoriesById[garment.categoryId]?.name?.let(OutfitSlot::classify)
    val categoryName = input.categoriesById[garment.categoryId]?.name ?: "unknown"
    return "- id=${garment.id.value}, slot=${slot?.name ?: "UNKNOWN"}, category=$categoryName, " +
        "colorId=${garment.primaryColorId?.value}"
}

private data class ParsedCloudOutfit(
    val picks: Map<String, Long>,
    val reasoning: String,
    val confidence: Float?,
)

/** Never crashes or fabricates on a malformed/unexpected response — an
 * unparseable body, a missing "outfits" array, or a non-numeric pick each
 * simply drop that one entry (Constitution rule 4), mirroring
 * [com.wardrobe.app.core.data.ai.parseMetadataSuggestions]'s discipline. */
private fun parseCloudOutfitSuggestions(rawResponseText: String): List<ParsedCloudOutfit> {
    val root = runCatching { STYLING_JSON.parseToJsonElement(rawResponseText).jsonObject }.getOrNull()
    val outfitsArray = root?.get("outfits") as? JsonArray
    return outfitsArray?.mapNotNull { element -> parseSingleCloudOutfit(element.jsonObject) }.orEmpty()
}

private fun parseSingleCloudOutfit(obj: JsonObject): ParsedCloudOutfit? {
    val picks =
        (obj["picks"] as? JsonObject)
            ?.mapNotNull { (slotName, value) -> value.jsonPrimitive.longOrNull?.let { slotName to it } }
            ?.toMap()
            .orEmpty()
    val reasoning = obj["reasoning"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull?.takeIf { it in 0f..1f }
    return if (picks.isNotEmpty()) ParsedCloudOutfit(picks, reasoning, confidence) else null
}
