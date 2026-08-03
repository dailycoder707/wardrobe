package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.weather.WeatherCondition
import java.time.LocalDate

/** A rough, honestly-labeled comfort estimate aggregated from member garments'
 * `Fit` — never a real fabric-science measurement. */
enum class ComfortLevel { RELAXED, MODERATE, STRUCTURED }

/** Aggregated from member garments' `warmthRating` (1..5) — the same scale
 * the Phase 6 styling engine's weather filter already uses. */
enum class WarmthLevel { LIGHT, MODERATE, WARM }

/**
 * A derived rating for one outfit, built from Phase 6's existing
 * [com.wardrobe.app.core.model.styling.Feedback] up/down votes — this app has
 * no other rating concept anywhere, and Phase 9 deliberately doesn't add a new
 * one (see `phase-9-smart-wardrobe-intelligence.md`). `null` (never a
 * fabricated `0` or `3`) when [totalVotes] is `0` — the outfit hasn't been
 * rated at all yet.
 */
data class OutfitRating(
    val positiveVotes: Int,
    val totalVotes: Int,
) {
    init {
        require(totalVotes > 0) { "OutfitRating should only be constructed once at least one vote exists" }
        require(positiveVotes in 0..totalVotes) { "positiveVotes must be between 0 and totalVotes" }
    }

    /** A 0..5 star-equivalent purely for display — not a second source of truth,
     * always recomputed from the two counts above. */
    val stars: Double get() = (positiveVotes.toDouble() / totalVotes) * MAX_STARS

    private companion object {
        const val MAX_STARS = 5.0
    }
}

/**
 * Everything Phase 9's Outfit Detail screen shows about one saved outfit,
 * derived from `WearEvent`/`Feedback`/the outfit's own already-stored
 * `seasons`/`dressCodes` — never re-storing what's already on
 * [com.wardrobe.app.core.model.outfit.Outfit] itself.
 */
data class OutfitInsights(
    val outfitId: OutfitId,
    val lastWornDate: LocalDate?,
    val timesWorn: Int,
    val averageRating: OutfitRating?,
    val isFavorite: Boolean,
    val suitableSeasons: Set<Season>,
    val suitableDressCodes: Set<DressCode>,
    /** Occasions whose [com.wardrobe.app.core.model.outfit.impliedDressCode]
     * matches one of [suitableDressCodes] — a heuristic derived match, not a
     * stored occasion history (the schema has no per-outfit occasion log
     * beyond the single `occasionId` each `WearEvent` already carries). */
    val suitableOccasionIds: Set<OccasionId>,
    val suitableWeather: Set<WeatherCondition>,
    val estimatedComfort: ComfortLevel,
    val estimatedWarmth: WarmthLevel,
    /** `0..100`, same scale and "overdue vs. recent" meaning as
     * [GarmentInsights.rotationScore], computed the same way from this
     * outfit's own wear history instead of a garment's. */
    val rotationPriority: Int?,
)
