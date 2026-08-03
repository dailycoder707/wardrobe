package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.CostPerWearRow
import com.wardrobe.app.core.database.dao.DormantGarmentRow
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.intelligence.ForgottenBucket
import com.wardrobe.app.core.model.intelligence.ForgottenGarment
import com.wardrobe.app.core.model.intelligence.NeverWornGarment
import com.wardrobe.app.core.model.intelligence.NeverWornReason
import com.wardrobe.app.core.model.intelligence.OverusedGarment
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Phase 9 — [WardrobeIntelligenceRepositoryImpl]'s `observeWardrobeAlerts()`
 * builders (forgotten/overused/never-worn), split out for the same
 * `TooManyFunctions`-avoidance reason as `GarmentAndOutfitInsightsBuilders.kt`. */

private const val OVERUSED_MULTIPLIER = 2.0
private const val RECENTLY_ADDED_DAYS = 30

internal fun dormantCutoff(clock: Clock): String =
    LocalDate.now(clock).minusDays(ForgottenBucket.THIRTY.days.toLong()).toString()

internal fun buildForgottenGarments(
    rows: List<DormantGarmentRow>,
    clock: Clock,
): List<ForgottenGarment> {
    val today = LocalDate.now(clock)
    return rows.mapNotNull { row ->
        val lastWorn = row.lastWornDate?.let(LocalDate::parse) ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(lastWorn, today).toInt()
        forgottenBucketFor(days)?.let { ForgottenGarment(GarmentId(row.garmentId), days, it) }
    }
}

internal fun forgottenBucketFor(daysSinceWorn: Int): ForgottenBucket? =
    ForgottenBucket.entries.filter { daysSinceWorn >= it.days }.maxByOrNull { it.days }

internal fun buildOverusedGarments(rows: List<CostPerWearRow>): List<OverusedGarment> {
    if (rows.isEmpty()) return emptyList()
    val average = rows.map { it.wearCount }.average()
    return if (average <= 0.0) {
        emptyList()
    } else {
        rows
            .filter { it.wearCount >= average * OVERUSED_MULTIPLIER }
            .map { OverusedGarment(GarmentId(it.garmentId), it.wearCount, average) }
    }
}

internal fun buildNeverWornGarments(
    costRows: List<CostPerWearRow>,
    garments: List<Garment>,
    clock: Clock,
): List<NeverWornGarment> {
    val neverWornIds = costRows.filter { it.wearCount == 0 }.map { it.garmentId }.toSet()
    val today = LocalDate.now(clock)
    return garments.filter { it.id.value in neverWornIds }.map { garment ->
        val addedDate = garment.createdAt.atZone(clock.zone).toLocalDate()
        val daysSinceAdded = ChronoUnit.DAYS.between(addedDate, today).toInt()
        val reason =
            if (daysSinceAdded <= RECENTLY_ADDED_DAYS) {
                NeverWornReason.RECENTLY_ADDED
            } else {
                NeverWornReason.PURCHASED_LONG_AGO
            }
        NeverWornGarment(garment.id, reason, daysSinceAdded)
    }
}
