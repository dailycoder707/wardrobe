package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import java.time.LocalDate

/**
 * Everything Phase 9's Garment Detail screen shows about one garment, derived
 * entirely from existing `WearEvent`/`Garment`/`PackingListItem` data — never a
 * second, independently-tracked copy of a number the database already holds
 * (Constitution: never duplicate stored statistics). [rotationScore] and
 * [averageDaysBetweenWears] are `null` when fewer than two wears exist — an
 * honest "insufficient history" rather than a fabricated number from one data
 * point.
 */
data class GarmentInsights(
    val garmentId: GarmentId,
    val lastWornDate: LocalDate?,
    val firstWornDate: LocalDate?,
    val totalWears: Int,
    val averageDaysBetweenWears: Double?,
    /** Wears per 30-day period since [firstWornDate] — `0.0` if never worn. */
    val wearFrequencyPerMonth: Double,
    /** `0..100`; `50` = right on this garment's own historical rewear schedule,
     * `>50` = overdue, `<50` = worn recently relative to its own pattern. See
     * `WardrobeIntelligenceRepositoryImpl`'s KDoc for the exact formula. */
    val rotationScore: Int?,
    val seasonUsage: Map<Season, Int>,
    val costPerWear: Double?,
    val isFavorite: Boolean,
    val status: GarmentStatus,
    val isInLaundry: Boolean,
    /** Non-null (the trip's own name) when this garment is on an active or
     * upcoming trip's packing list right now. */
    val packedForTripName: String?,
)
