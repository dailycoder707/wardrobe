package com.wardrobe.app.core.model.stats

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import java.time.LocalDate

/** All derived, never persisted as a source of truth — ADR-006. */
enum class StatsWindow { ONE_MONTH, SIX_MONTHS, ONE_YEAR, ALL_TIME }

data class UsageStats(
    val window: StatsWindow,
    val totalActiveGarments: Int,
    val wornAtLeastOnce: Int,
    val usagePercent: Double,
    val mostWornGarmentIds: List<GarmentId>,
    val leastWornGarmentIds: List<GarmentId>,
    val favouriteBrandIds: List<BrandId>,
    val signatureColorIds: List<ColorId>,
    /** Added Phase 5e — the same top-N-by-wear-count pattern as
     * [favouriteBrandIds]/[signatureColorIds], joined directly on
     * `garments.categoryId` rather than a cross-ref table. */
    val favouriteCategoryIds: List<CategoryId>,
    val wearsBySeason: Map<Season, Int>,
    val wearsByDressCode: Map<DressCode, Int>,
    val weekdayWearCount: Int,
    val weekendWearCount: Int,
    /** Phase 9 additions — folded into this already-bag-shaped struct rather
     * than three new `StatsRepository` methods, which would have pushed that
     * interface over detekt's `TooManyFunctions` threshold for no real
     * benefit (all three are already-window-scoped-or-all-time dashboard
     * stats, exactly this type's existing charter). */
    val categoryWearCounts: List<CategoryWearCountEntry> = emptyList(),
    val garmentVersatility: List<GarmentVersatilityEntry> = emptyList(),
    val topGarmentCombinations: List<GarmentCombinationEntry> = emptyList(),
)

data class CostPerWearEntry(
    val garmentId: GarmentId,
    val totalWearCount: Int,
    val costPerWear: Double?,
    val lastWornDate: LocalDate?,
)

/** A dormant item: zero wears (direct or via an outfit) within [window]. */
data class DormantItem(
    val garmentId: GarmentId,
    val lastWornDate: LocalDate?,
)

/**
 * A derived, explainable statement about what the wardrobe lacks, based on
 * occasion/season coverage — not on what a shop wants to sell
 * (phase-1-architecture.md Section 2).
 */
data class ClosetGap(
    val description: String,
    val relatedCategoryId: CategoryId?,
    val relatedSeason: Season?,
    val relatedDressCode: DressCode?,
)

/** One day's wear count — Phase 5e's Usage Heatmap, and the raw material
 * `feature:stats` buckets into monthly/weekly views in Kotlin rather than
 * adding a separate SQL query per granularity. */
data class WearHeatmapDay(
    val date: LocalDate,
    val wearCount: Int,
)

/** A saved outfit worn more than once in the window — "Frequently Repeated
 * Looks." Counts only wear events logged directly against the outfit (an
 * outfit "wear" is always a deliberate log-the-whole-look action, never
 * inferred from its garments' own individual wear counts). */
data class RepeatedOutfit(
    val outfitId: OutfitId,
    val wearCount: Int,
)

/** Phase 9 — every category's wear count, not just the top N
 * [UsageStats.favouriteCategoryIds] already exposes; backs Style Insights'
 * per-slot "favorite footwear/bag/jewelry/accessories" breakdown. */
data class CategoryWearCountEntry(
    val categoryId: CategoryId,
    val wearCount: Int,
)

/** Phase 9 — how many distinct saved outfits a garment appears in,
 * independent of how many times it's been worn — "most/least versatile
 * garments." */
data class GarmentVersatilityEntry(
    val garmentId: GarmentId,
    val outfitCount: Int,
)

/** Phase 9 — "favorite combinations": how often two garments appear
 * together in the same saved outfit. */
data class GarmentCombinationEntry(
    val garmentIdA: GarmentId,
    val garmentIdB: GarmentId,
    val pairCount: Int,
)

/** M21 — active-garment counts by fiber-content material (Cotton, Wool…),
 * only for materials genuinely tagged on at least one garment — a material
 * nobody owns has nothing to report, unlike [OccasionCoverageEntry] where a
 * zero-count occasion is itself the useful signal. */
data class MaterialDistributionEntry(
    val materialId: MaterialId,
    val garmentCount: Int,
)

/** M21 — active-garment counts by weave/construction fabric (Denim, Jersey…),
 * deliberately distinct from [MaterialDistributionEntry] (core:model's
 * `Material`/`Fabric` split, M19 ADR-018). */
data class FabricDistributionEntry(
    val fabricId: FabricId,
    val garmentCount: Int,
)

/** M21 — how many active garments are tagged for each real [Occasion]
 * reference row, including occasions with zero garments (the coverage gap
 * itself) — the same "count including zero" shape [ClosetGap] already uses
 * for season/dress-code coverage. */
data class OccasionCoverageEntry(
    val occasionId: OccasionId,
    val garmentCount: Int,
)

/** M21 — bundles two previously-separate, parameterless "orphan" queries
 * (a saved outfit never once worn; a garment never placed in any saved
 * outfit) behind one [StatsRepository][com.wardrobe.app.core.domain.repository.StatsRepository]
 * method rather than two — the same fold used for [WardrobeMixDistribution],
 * and for the same reason (keeping that interface under detekt's
 * `TooManyFunctions` threshold without an unprecedented suppression).
 * Scoped to `feature:stats`, the sole consumer of both fields. */
data class WardrobeUsageGaps(
    val neverWornOutfitIds: List<OutfitId>,
    val garmentsMissingOutfits: List<GarmentId>,
)

/** M21 — bundles [MaterialDistributionEntry]/[FabricDistributionEntry]/
 * [OccasionCoverageEntry] behind one [StatsRepository][com.wardrobe.app.core.domain.repository.StatsRepository]
 * method rather than three, the same "fold into the model" choice this
 * type's own Phase 9 fields already made — see that method's KDoc. */
data class WardrobeMixDistribution(
    val materials: List<MaterialDistributionEntry>,
    val fabrics: List<FabricDistributionEntry>,
    val occasions: List<OccasionCoverageEntry>,
    /** Part 6 — how many active garments have no occasion tagged at all,
     * disclosed honestly rather than letting [occasions] imply full
     * coverage. */
    val garmentsWithoutOccasion: Int,
)
