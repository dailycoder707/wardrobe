package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.intelligence.CapsuleSuggestion
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.model.intelligence.DailyBrief
import com.wardrobe.app.core.model.intelligence.DuplicateGroup
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.ShoppingGapSuggestion
import com.wardrobe.app.core.model.intelligence.WardrobeAlerts
import com.wardrobe.app.core.model.intelligence.WardrobeHealthScore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Phase 9 — every "smart wardrobe" derived insight this app surfaces,
 * grouped separately from [StatsRepository] (which backs the window-based
 * Style Insights dashboard) because these are per-item/rule-based
 * intelligence, not aggregate dashboard stats. Every method here is a
 * pure computation over data `GarmentRepository`/`OutfitRepository`/
 * `WearEventRepository`/`TripRepository` already own — nothing is
 * persisted as a new source of truth (Constitution: never duplicate
 * stored statistics).
 */
interface WardrobeIntelligenceRepository {
    fun observeGarmentInsights(garmentId: GarmentId): Flow<GarmentInsights?>

    fun observeOutfitInsights(outfitId: OutfitId): Flow<OutfitInsights?>

    fun observeWardrobeAlerts(): Flow<WardrobeAlerts>

    fun observeShoppingGaps(): Flow<List<ShoppingGapSuggestion>>

    fun observeDuplicateGroups(): Flow<List<DuplicateGroup>>

    suspend fun suggestCapsule(type: CapsuleType): CapsuleSuggestion

    fun observeWardrobeHealthScore(): Flow<WardrobeHealthScore>

    fun observeCalendarConflicts(lookAheadDays: Int): Flow<List<CalendarConflict>>

    suspend fun buildDailyBrief(
        today: LocalDate,
        greeting: String,
    ): DailyBrief
}
