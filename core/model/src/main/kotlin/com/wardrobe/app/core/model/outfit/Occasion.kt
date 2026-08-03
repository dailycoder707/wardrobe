package com.wardrobe.app.core.model.outfit

import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.garment.DressCode

/** User-extensible (phase-3-persistence.md) — unlike [Season]/[DressCode], this is a
 * real reference table since new occasions are expected to be added over time. */
data class Occasion(
    val id: OccasionId,
    val name: String,
)

private val BUSINESS_KEYWORDS = listOf("work", "office", "business", "meeting")
private val FORMAL_KEYWORDS = listOf("formal", "wedding", "gala")
private val ATHLETIC_KEYWORDS = listOf("athletic", "gym", "sport", "workout")
private val SMART_CASUAL_KEYWORDS = listOf("dinner", "date", "event")
private val LOUNGE_KEYWORDS = listOf("lounge", "home")
private val CASUAL_KEYWORDS = listOf("casual", "travel", "everyday")

/**
 * A keyword heuristic mapping an occasion's own name to the [DressCode] it
 * typically implies (Phase 7) — this schema has no direct `Occasion` →
 * `DressCode` link (`Occasion` is a free-form, user-extensible reference
 * table, phase-3-persistence.md), so a calendar event tagged "Wedding" or
 * "Office" needs *some* way to bias recommendation scoring toward a
 * plausible dress code without a hardcoded occasion enum. Explicitly labeled
 * as a heuristic, not a guarantee — the same "good enough, honestly stated"
 * pattern `OutfitSlot.classify`/`AccessoryCategory.classify` already use. An
 * unrecognized occasion name yields `null`, and the engine simply doesn't
 * bias scoring for that day rather than guessing wrong.
 */
fun Occasion.impliedDressCode(): DressCode? {
    val lower = name.lowercase()
    return when {
        FORMAL_KEYWORDS.any { it in lower } -> DressCode.FORMAL
        BUSINESS_KEYWORDS.any { it in lower } -> DressCode.BUSINESS
        ATHLETIC_KEYWORDS.any { it in lower } -> DressCode.ATHLETIC
        LOUNGE_KEYWORDS.any { it in lower } -> DressCode.LOUNGE
        SMART_CASUAL_KEYWORDS.any { it in lower } -> DressCode.SMART_CASUAL
        CASUAL_KEYWORDS.any { it in lower } -> DressCode.CASUAL
        else -> null
    }
}
