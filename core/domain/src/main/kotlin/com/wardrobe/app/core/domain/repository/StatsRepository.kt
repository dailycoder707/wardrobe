package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WardrobeMixDistribution
import com.wardrobe.app.core.model.stats.WardrobeUsageGaps
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

    /** M21 — folds the former `observeNeverWornOutfitIds`/
     * `observeGarmentsMissingOutfits` pair into one method; see
     * [WardrobeUsageGaps]'s KDoc. */
    fun observeWardrobeUsageGaps(): Flow<WardrobeUsageGaps>

    fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int>

    /** Wardrobe Story's "you dress differently on weekends" — the single
     * dress code with the most wears logged on that day type, `null` if
     * nothing's been logged for it yet in the window. */
    fun observeTopDressCode(
        window: StatsWindow,
        isWeekend: Boolean,
    ): Flow<DressCode?>

    /** M21 — composition-based (not wear-based) counts, the same shape
     * [observeClosetGaps] already uses for season/dress-code coverage: how
     * many *active* garments are tagged with each material/fabric/occasion,
     * independent of whether they've ever been worn. Folded into one method
     * returning [WardrobeMixDistribution] rather than four separate ones —
     * the same "fold into the model, not the interface" choice [UsageStats]'
     * own Phase 9 additions already made, for the identical reason (keeping
     * this interface under detekt's `TooManyFunctions` threshold). */
    fun observeWardrobeMixDistribution(): Flow<WardrobeMixDistribution>

    companion object {
        const val DEFAULT_TOP_N = 5
    }
}
