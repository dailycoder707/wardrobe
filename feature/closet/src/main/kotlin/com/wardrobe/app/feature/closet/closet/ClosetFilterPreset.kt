package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.garment.DressCode

/**
 * Deterministic filter shortcuts translating a familiar activity name into a
 * [DressCode] set — reusing the exact same business/athletic/smart-casual/
 * casual keyword categorization
 * [com.wardrobe.app.core.model.outfit.impliedDressCode] already established
 * for Recommendations, rather than inventing new classification rules (M17
 * non-negotiable). No new persisted state: applying a preset just replaces
 * [ClosetFilterState.dressCodes], so it remains an ordinary, fully
 * overridable/removable filter like any other — visible as normal "Dress
 * Code" chips in [ActiveFilterChipsRow], not a separate concept.
 *
 * "Occasion"-based presets (matching the user's own free-text [Occasion]
 * reference rows, e.g. an occasion literally named "Travel") were
 * deliberately excluded — `Occasion` is user-extensible free text with no
 * guaranteed rows, so a preset built on it could silently return nothing for
 * a user who never created a matching occasion. `DressCode` is a fixed
 * enum every garment can carry, so it is the only honest, always-available
 * signal for a preset (documented in ADR-016).
 */
enum class ClosetFilterPreset(
    val label: String,
    val dressCodes: Set<DressCode>,
) {
    WORK("Work", setOf(DressCode.BUSINESS, DressCode.SMART_CASUAL)),
    CASUAL("Casual", setOf(DressCode.CASUAL)),
    TRAVEL("Travel", setOf(DressCode.CASUAL, DressCode.SMART_CASUAL)),
    DATE_NIGHT("Date Night", setOf(DressCode.SMART_CASUAL)),
    ATHLETIC("Athletic", setOf(DressCode.ATHLETIC)),
}
