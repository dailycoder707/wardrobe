package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.ScoredOutfit
import java.time.Instant

private const val BASE_CLOUD_SCORE = 5.0

/**
 * M12's validation gate for Cloud Outfit Styling (ADR-013) — a cloud-
 * suggested slot→garment mapping is never trusted as-is. Every pick must be
 * a real wardrobe item that actually belongs to its claimed slot (i.e. would
 * have been offered by [buildSlotCandidates] itself) and passes every rule a
 * rule-engine-generated outfit already has to pass: the weather filter, the
 * avoid-category/avoid-brand exclusion [buildSlotCandidates] applies before
 * this function ever sees a candidate, the whole-outfit rules
 * ([passesWholeOutfitRules]), the dress-or-top+bottom invariant
 * ([pickTorsoSlots] mirrors it), and no garment reused across two slots.
 * `null` means the proposal failed validation and must never reach the
 * user — the caller (`StylingEngineRouter`) falls back to the on-device rule
 * engine rather than surfacing a partially-hallucinated outfit, exactly the
 * same "cloud degrades this capability, it never breaks it" contract every
 * other Router already follows.
 */
internal fun validateCloudOutfit(
    picks: Map<OutfitSlot, GarmentId>,
    input: EngineInput,
    reasoning: String,
    confidence: Float?,
    provenance: AiResultProvenance,
    requiredAnchor: GarmentId? = null,
): ScoredOutfit? {
    val resolved = resolveCloudPicks(picks, input)
    val isValid =
        resolved != null &&
            hasCompleteTorso(resolved.keys) &&
            (requiredAnchor == null || resolved.values.any { it.garment.id == requiredAnchor }) &&
            passesWholeOutfitRules(resolved.values.map { it.garment }, input)
    if (!isValid) return null

    val validated = requireNotNull(resolved)
    val outfitGarments = validated.map { (slot, candidate) -> OutfitGarmentSlot(candidate.garment.id, slot.slotIndex) }
    val outfit =
        Outfit(
            id = OutfitId(0),
            name = null,
            garments = outfitGarments,
            occasionId = null,
            source = OutfitSource.AI_SUGGESTED,
            isSaved = false,
            photoUri = null,
            createdAt = Instant.now(),
        )
    return ScoredOutfit(
        outfit = outfit,
        score = validated.values.sumOf { it.score },
        explanation = reasoning.ifBlank { "Suggested by AI styling." },
        passedWeatherFilter = true,
        provenance = provenance,
        aiConfidence = confidence,
    )
}

/** `null` if any pick references a nonexistent garment, a garment that
 * doesn't actually belong to its claimed slot, or the same garment reused
 * across two slots — never silently drops one bad pick and keeps going,
 * since a partially-invented outfit is exactly what this gate exists to
 * catch. */
private fun resolveCloudPicks(
    picks: Map<OutfitSlot, GarmentId>,
    input: EngineInput,
): Map<OutfitSlot, ScoredCandidate>? {
    val slotCandidates = buildSlotCandidates(input)
    val usedGarmentIds = mutableSetOf<GarmentId>()
    val resolved = mutableMapOf<OutfitSlot, ScoredCandidate>()
    val allResolved =
        picks.all { (slot, garmentId) ->
            val candidate = resolvePick(slot, garmentId, input, slotCandidates, usedGarmentIds)
            if (candidate != null) {
                resolved[slot] = candidate
                usedGarmentIds += garmentId
            }
            candidate != null
        }
    return if (allResolved) resolved else null
}

private fun resolvePick(
    slot: OutfitSlot,
    garmentId: GarmentId,
    input: EngineInput,
    slotCandidates: Map<OutfitSlot, List<Garment>>,
    usedGarmentIds: Set<GarmentId>,
): ScoredCandidate? {
    val garment = input.garmentsById[garmentId]
    val belongsToSlot = slotCandidates[slot].orEmpty().any { it.id == garmentId }
    val isValid =
        garment != null &&
            garmentId !in usedGarmentIds &&
            belongsToSlot &&
            passesWeatherFilter(garment, input.weather)
    return if (isValid) ScoredCandidate(requireNotNull(garment), BASE_CLOUD_SCORE, emptyList()) else null
}

private fun hasCompleteTorso(slots: Set<OutfitSlot>): Boolean =
    OutfitSlot.DRESS in slots || (OutfitSlot.TOP in slots && OutfitSlot.BOTTOM in slots)
