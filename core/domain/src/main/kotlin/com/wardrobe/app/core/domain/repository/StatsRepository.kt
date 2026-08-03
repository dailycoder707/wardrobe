package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import kotlinx.coroutines.flow.Flow

/** Every method is a derived query — ADR-006. Nothing here reads a precomputed
 * "Statistics" table as a source of truth. */
interface StatsRepository {
    fun observeUsageStats(window: StatsWindow): Flow<UsageStats>

    fun observeCostPerWear(): Flow<List<CostPerWearEntry>>

    fun observeDormantItems(window: StatsWindow): Flow<List<DormantItem>>

    fun observeClosetGaps(): Flow<List<ClosetGap>>

    /** Phase 5e — Wardrobe Intelligence additions, all still plain derived
     * queries over `garments`/`wear_events`/`outfits`/`outfit_garments`. */
    fun observeWearHeatmap(range: DateRange): Flow<List<WearHeatmapDay>>

    fun observeRepeatedOutfits(
        window: StatsWindow,
        limit: Int = DEFAULT_TOP_N,
    ): Flow<List<RepeatedOutfit>>

    fun observeNeverWornOutfitIds(): Flow<List<OutfitId>>

    fun observeGarmentsMissingOutfits(): Flow<List<GarmentId>>

    fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int>

    /** Wardrobe Story's "you dress differently on weekends" — the single
     * dress code with the most wears logged on that day type, `null` if
     * nothing's been logged for it yet in the window. */
    fun observeTopDressCode(
        window: StatsWindow,
        isWeekend: Boolean,
    ): Flow<DressCode?>

    companion object {
        const val DEFAULT_TOP_N = 5
    }
}
