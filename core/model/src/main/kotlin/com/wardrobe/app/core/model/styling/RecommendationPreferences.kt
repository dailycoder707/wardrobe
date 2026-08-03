package com.wardrobe.app.core.model.styling

import com.wardrobe.app.core.model.garment.DressCode

/**
 * "Stylist Preferences" (Phase 6) — persisted via `StylistPreferencesRepository`
 * (DataStore-backed, mirrors `ClosetPreferencesRepository`'s pattern). Every field
 * has a sensible, non-surprising default so a user who never opens the preferences
 * screen still gets complete, varied outfits rather than a degraded experience.
 *
 * [preferredDressCodes] deliberately reuses the existing fixed [DressCode] vocabulary
 * rather than inventing a new "style" taxonomy this schema has no other use for —
 * an empty set means "no preference," not "nothing matches."
 */
data class RecommendationPreferences(
    val includeShoes: Boolean = true,
    val includeBags: Boolean = true,
    val includeJewelry: Boolean = true,
    val includeWatch: Boolean = true,
    val includeBelt: Boolean = true,
    val includeHairAccessories: Boolean = false,
    val includeSunglasses: Boolean = false,
    /** Added Phase 9 — the one [com.wardrobe.app.core.model.styling.AccessoryCategory]
     * value that had no dedicated toggle before now. */
    val includeScarf: Boolean = false,
    val preferFavorites: Boolean = true,
    val preferRarelyWorn: Boolean = false,
    val avoidRecentlyWorn: Boolean = true,
    val maxRepeatIntervalDays: Int = DEFAULT_MAX_REPEAT_INTERVAL_DAYS,
    val favoriteColorBias: Boolean = true,
    val preferredDressCodes: Set<DressCode> = emptySet(),
    val preferComfortableFit: Boolean = false,
) {
    companion object {
        /** A week — long enough that the same look won't feel repetitive, short
         * enough not to starve a smaller wardrobe of viable outfits. */
        const val DEFAULT_MAX_REPEAT_INTERVAL_DAYS = 7
    }
}
