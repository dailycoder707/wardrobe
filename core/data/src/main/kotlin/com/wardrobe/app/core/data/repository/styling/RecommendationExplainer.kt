package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.domain.styling.ColorHarmony
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory

private const val MAX_REASON_SENTENCES = 3

/**
 * Joins the strongest reasons across every picked slot into one or two natural
 * sentences — Constitution: no black-box suggestions, and never technical
 * wording (see the master brief's own examples: "This blazer hasn't been worn
 * for 24 days," "These shoes match the handbag"). Picks the anchor item's own
 * reason first when one exists (`suggestForItem`), then the rest in slot order,
 * capped at [MAX_REASON_SENTENCES] so the explanation stays a glance, not a report.
 */
internal fun buildExplanation(
    picks: Map<OutfitSlot, ScoredCandidate>,
    harmony: ColorHarmony?,
): String {
    val sentences = mutableListOf<String>()
    picks.values
        .flatMap { it.reasons }
        .distinct()
        .take(MAX_REASON_SENTENCES)
        .forEach { reason -> sentences += reason.replaceFirstChar(Char::uppercase) }

    if (harmony != null && harmony != ColorHarmony.MIXED && sentences.size < MAX_REASON_SENTENCES) {
        sentences += harmonyPhrase(harmony)
    }

    return if (sentences.isEmpty()) {
        "A complete outfit built from what's currently available."
    } else {
        sentences.joinToString(" ") { "$it." }
    }
}

/** Phase 9 — one sentence per accessory/jewelry item, reusing the same
 * [ScoredCandidate.reasons] the whole-outfit [buildExplanation] already draws
 * from, never a second explanation generator. Falls back to a plain
 * completion sentence for an item whose only "reason" is the base score
 * (nothing favorited/rare/color-matched/etc. applied). */
private fun readableCategoryName(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

internal fun explainAccessoryItem(
    candidate: ScoredCandidate,
    category: AccessoryCategory,
): String {
    val reason = candidate.reasons.firstOrNull()
    return if (reason != null) {
        "${reason.replaceFirstChar(Char::uppercase)}."
    } else {
        "${readableCategoryName(category.name)} added to complete the look."
    }
}

internal fun explainJewelryItem(
    candidate: ScoredCandidate,
    category: JewelryCategory,
): String {
    val reason = candidate.reasons.firstOrNull()
    return if (reason != null) {
        "${reason.replaceFirstChar(Char::uppercase)}."
    } else {
        "${readableCategoryName(category.name)} added to complete the look."
    }
}

private fun harmonyPhrase(harmony: ColorHarmony): String =
    when (harmony) {
        ColorHarmony.MONOCHROME -> "The colors work together as a single tone"
        ColorHarmony.ANALOGOUS -> "The colors sit close together on the color wheel"
        ColorHarmony.COMPLEMENTARY -> "The colors are a classic complementary pair"
        ColorHarmony.NEUTRAL -> "It leans neutral, so it's easy to wear"
        ColorHarmony.MIXED -> "" // filtered out before this is reached
    }
