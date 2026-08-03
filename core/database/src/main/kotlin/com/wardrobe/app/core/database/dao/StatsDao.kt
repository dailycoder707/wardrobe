package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CostPerWearRow(
    val garmentId: Long,
    val price: Double?,
    val wearCount: Int,
    val lastWornDate: String?,
)

data class DormantGarmentRow(
    val garmentId: Long,
    val lastWornDate: String?,
)

data class SeasonWearCountRow(
    val season: String,
    val wearCount: Int,
)

data class DressCodeWearCountRow(
    val dressCode: String,
    val wearCount: Int,
)

data class BrandWearCountRow(
    val brandId: Long,
    val wearCount: Int,
)

data class ColorWearCountRow(
    val colorId: Long,
    val wearCount: Int,
)

data class WeekdayWeekendRow(
    val isWeekend: Boolean,
    val wearCount: Int,
)

/** Closet *composition* counts (Gap Analysis) — how many active garments cover a
 * season/dress-code, independent of whether they've ever been worn. Distinct from
 * the wear-count rows above, which are about wear history. */
data class SeasonGarmentCountRow(
    val season: String,
    val garmentCount: Int,
)

data class DressCodeGarmentCountRow(
    val dressCode: String,
    val garmentCount: Int,
)

/** Phase 5e — added for Wardrobe Intelligence. */
data class CategoryWearCountRow(
    val categoryId: Long,
    val wearCount: Int,
)

data class DateWearCountRow(
    val date: String,
    val wearCount: Int,
)

data class OutfitWearCountRow(
    val outfitId: Long,
    val wearCount: Int,
)

/** Phase 9 — how many active garments exist per top-level category, for
 * Shopping Gap Analysis's "you own 16 tops but only 2 handbags" comparison. */
data class CategoryGarmentCountRow(
    val categoryId: Long,
    val garmentCount: Int,
)

/** Phase 9 — "most/least versatile garments": how many distinct outfits a
 * garment has been placed into, independent of how many times it's been worn. */
data class GarmentOutfitCountRow(
    val garmentId: Long,
    val outfitCount: Int,
)

/** Phase 9 — "favorite combinations": how often two garments appear together
 * in the same saved outfit. */
data class GarmentPairRow(
    val garmentIdA: Long,
    val garmentIdB: Long,
    val pairCount: Int,
)

/**
 * Every method here is a derived query over `garments`/`wear_events`/
 * `outfit_garments` — none read a precomputed table (ADR-006). All return `Flow` (not
 * `suspend`) so `StatsRepositoryImpl` can combine them into a live-updating
 * `UsageStats`/`ClosetGap` stream — Room's invalidation tracker re-runs a `@Query`
 * automatically when any table it references changes, even for the multi-table
 * `WITH`-CTE queries below. The recurring `all_wears` CTE is a garment's total wear
 * history counted from **both** sources: wear events logged directly against it, and
 * wear events logged against any outfit that contains it — see
 * phase-3-persistence.md's "cost-per-wear" section for why both must count.
 */
@Dao
interface StatsDao {
    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT g.id AS garmentId, g.price AS price, COUNT(aw.date) AS wearCount,
               MAX(aw.date) AS lastWornDate
        FROM garments g
        LEFT JOIN all_wears aw ON aw.garmentId = g.id
        WHERE g.status = 'ACTIVE'
        GROUP BY g.id
        """,
    )
    fun observeCostPerWear(): Flow<List<CostPerWearRow>>

    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT g.id AS garmentId, MAX(aw.date) AS lastWornDate
        FROM garments g
        LEFT JOIN all_wears aw ON aw.garmentId = g.id
        WHERE g.status = 'ACTIVE'
        GROUP BY g.id
        HAVING lastWornDate IS NULL OR lastWornDate < :sinceDate
        """,
    )
    fun observeDormantSince(sinceDate: String): Flow<List<DormantGarmentRow>>

    @Query("SELECT COUNT(*) FROM garments WHERE status = 'ACTIVE'")
    fun observeTotalActiveGarmentCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT garmentId) FROM (
            SELECT garmentId FROM wear_events
            WHERE garmentId IS NOT NULL AND status = 'WORN' AND date BETWEEN :startDate AND :endDate
            UNION ALL
            SELECT og.garmentId AS garmentId
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN' AND we.date BETWEEN :startDate AND :endDate
        )
        """,
    )
    fun observeGarmentsWornInRange(
        startDate: String,
        endDate: String,
    ): Flow<Int>

    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT gs.season AS season, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garment_seasons gs ON gs.garmentId = aw.garmentId
        WHERE aw.date BETWEEN :startDate AND :endDate
        GROUP BY gs.season
        """,
    )
    fun observeWearCountBySeason(
        startDate: String,
        endDate: String,
    ): Flow<List<SeasonWearCountRow>>

    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT gd.dressCode AS dressCode, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garment_dress_codes gd ON gd.garmentId = aw.garmentId
        WHERE aw.date BETWEEN :startDate AND :endDate
        GROUP BY gd.dressCode
        """,
    )
    fun observeWearCountByDressCode(
        startDate: String,
        endDate: String,
    ): Flow<List<DressCodeWearCountRow>>

    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT g.brandId AS brandId, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garments g ON g.id = aw.garmentId
        WHERE g.brandId IS NOT NULL AND aw.date BETWEEN :startDate AND :endDate
        GROUP BY g.brandId
        ORDER BY wearCount DESC
        LIMIT :limit
        """,
    )
    fun observeFavouriteBrands(
        startDate: String,
        endDate: String,
        limit: Int,
    ): Flow<List<BrandWearCountRow>>

    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT gc.colorId AS colorId, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garment_color_palette gc ON gc.garmentId = aw.garmentId
        WHERE aw.date BETWEEN :startDate AND :endDate
        GROUP BY gc.colorId
        ORDER BY wearCount DESC
        LIMIT :limit
        """,
    )
    fun observeSignatureColors(
        startDate: String,
        endDate: String,
        limit: Int,
    ): Flow<List<ColorWearCountRow>>

    /** `strftime('%w', date)` returns 0 (Sunday)..6 (Saturday) directly on the ISO-8601
     * `TEXT` date column — no epoch conversion needed, which is exactly why
     * `wear_events.date` is stored as text (phase-3-persistence.md). */
    @Query(
        """
        SELECT
            CAST(strftime('%w', date) AS INTEGER) IN (0, 6) AS isWeekend,
            COUNT(*) AS wearCount
        FROM wear_events
        WHERE status = 'WORN' AND date BETWEEN :startDate AND :endDate
        GROUP BY isWeekend
        """,
    )
    fun observeWeekdayVsWeekend(
        startDate: String,
        endDate: String,
    ): Flow<List<WeekdayWeekendRow>>

    @Query(
        """
        SELECT s.season AS season, COUNT(DISTINCT gs.garmentId) AS garmentCount
        FROM (SELECT 'SPRING' AS season UNION SELECT 'SUMMER' UNION SELECT 'AUTUMN' UNION SELECT 'WINTER') s
        LEFT JOIN garment_seasons gs ON gs.season = s.season
        LEFT JOIN garments g ON g.id = gs.garmentId AND g.status = 'ACTIVE'
        GROUP BY s.season
        """,
    )
    fun observeActiveGarmentCountBySeason(): Flow<List<SeasonGarmentCountRow>>

    @Query(
        """
        SELECT d.dressCode AS dressCode, COUNT(DISTINCT gd.garmentId) AS garmentCount
        FROM (
            SELECT 'CASUAL' AS dressCode UNION SELECT 'SMART_CASUAL' UNION SELECT 'BUSINESS'
            UNION SELECT 'FORMAL' UNION SELECT 'ATHLETIC' UNION SELECT 'LOUNGE'
        ) d
        LEFT JOIN garment_dress_codes gd ON gd.dressCode = d.dressCode
        LEFT JOIN garments g ON g.id = gd.garmentId AND g.status = 'ACTIVE'
        GROUP BY d.dressCode
        """,
    )
    fun observeActiveGarmentCountByDressCode(): Flow<List<DressCodeGarmentCountRow>>

    /** Phase 5e — "Favorite Categories," the same top-N-by-wear-count shape as
     * [observeFavouriteBrands], joined directly on `garments.categoryId` since
     * (unlike season/dress-code/color) a garment has exactly one category, no
     * cross-ref table involved. */
    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT g.categoryId AS categoryId, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garments g ON g.id = aw.garmentId
        WHERE aw.date BETWEEN :startDate AND :endDate
        GROUP BY g.categoryId
        ORDER BY wearCount DESC
        LIMIT :limit
        """,
    )
    fun observeFavouriteCategories(
        startDate: String,
        endDate: String,
        limit: Int,
    ): Flow<List<CategoryWearCountRow>>

    /** Phase 5e — the Usage Heatmap's raw material: one row per day that had at
     * least one logged wear event, counting *events* the same way
     * [observeWeekdayVsWeekend] does (a day where one outfit covering 5
     * garments was logged counts as 1, not 5 — this is a heatmap of "did you
     * get dressed and log it," not a garment-expanded wear tally).
     * `feature:stats` buckets these into monthly/weekly views in Kotlin rather
     * than adding one query per granularity — grouping an already
     * date-grouped list by month/ISO-week is cheap; re-querying SQLite for
     * each granularity would not be. */
    @Query(
        """
        SELECT date AS date, COUNT(*) AS wearCount
        FROM wear_events
        WHERE status = 'WORN' AND date BETWEEN :startDate AND :endDate
        GROUP BY date
        """,
    )
    fun observeWearCountByDate(
        startDate: String,
        endDate: String,
    ): Flow<List<DateWearCountRow>>

    /** Phase 5e — "Frequently Repeated Looks." Counts only wear events logged
     * directly against the outfit itself, not the outfit's constituent
     * garments' own wear counts — see [com.wardrobe.app.core.model.stats.RepeatedOutfit]. */
    @Query(
        """
        SELECT outfitId AS outfitId, COUNT(*) AS wearCount
        FROM wear_events
        WHERE outfitId IS NOT NULL AND status = 'WORN' AND date BETWEEN :startDate AND :endDate
        GROUP BY outfitId
        HAVING wearCount > 1
        ORDER BY wearCount DESC
        LIMIT :limit
        """,
    )
    fun observeOutfitWearCounts(
        startDate: String,
        endDate: String,
        limit: Int,
    ): Flow<List<OutfitWearCountRow>>

    /** Phase 5e — "Outfits Never Worn": saved, non-archived looks with zero
     * `WORN` wear events logged against them (a `PLANNED`-only or never-logged
     * outfit still counts as never worn). */
    @Query(
        """
        SELECT o.id
        FROM outfits o
        LEFT JOIN wear_events we ON we.outfitId = o.id AND we.status = 'WORN'
        WHERE o.isSaved = 1 AND o.isArchived = 0
        GROUP BY o.id
        HAVING COUNT(we.id) = 0
        """,
    )
    fun observeNeverWornOutfitIds(): Flow<List<Long>>

    /** Phase 5e — "Garments Missing Outfits": active garments that have never
     * been placed into any saved outfit's slots. */
    @Query(
        """
        SELECT g.id
        FROM garments g
        LEFT JOIN outfit_garments og ON og.garmentId = g.id
        WHERE g.status = 'ACTIVE'
        GROUP BY g.id
        HAVING COUNT(og.outfitId) = 0
        """,
    )
    fun observeGarmentsMissingOutfitIds(): Flow<List<Long>>

    /** Phase 5e — Wardrobe Story's "You've worn N outfits this year": every
     * `WORN` wear event logged against an outfit in the window, counted once
     * per event (a single outfit worn 3 times counts as 3). */
    @Query(
        """
        SELECT COUNT(*) FROM wear_events
        WHERE outfitId IS NOT NULL AND status = 'WORN' AND date BETWEEN :startDate AND :endDate
        """,
    )
    fun observeOutfitWearEventCount(
        startDate: String,
        endDate: String,
    ): Flow<Int>

    /** Phase 5e — Wardrobe Story's "You dress more casually on weekends" (or
     * whichever dress code actually dominates each day type) — the same
     * `strftime('%w', ...)` weekend test [observeWeekdayVsWeekend] uses,
     * scoped to one day type at a time so `feature:stats` can compare the
     * weekday-dominant and weekend-dominant dress code and only surface a
     * story card when they genuinely differ. */
    @Query(
        """
        WITH all_wears AS (
            SELECT garmentId, date FROM wear_events WHERE garmentId IS NOT NULL AND status = 'WORN'
            UNION ALL
            SELECT og.garmentId AS garmentId, we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE we.outfitId IS NOT NULL AND we.status = 'WORN'
        )
        SELECT gd.dressCode AS dressCode, COUNT(*) AS wearCount
        FROM all_wears aw
        JOIN garment_dress_codes gd ON gd.garmentId = aw.garmentId
        WHERE aw.date BETWEEN :startDate AND :endDate
          AND (CAST(strftime('%w', aw.date) AS INTEGER) IN (0, 6)) = :isWeekend
        GROUP BY gd.dressCode
        ORDER BY wearCount DESC
        """,
    )
    fun observeDressCodeByDayType(
        startDate: String,
        endDate: String,
        isWeekend: Boolean,
    ): Flow<List<DressCodeWearCountRow>>

    /** Phase 9 — the same dual-source `all_wears` CTE every query above
     * already uses (direct wears + wears logged via an outfit containing
     * this garment), scoped to one garment and returning the sorted date
     * list. Backs first-worn/most-recent/total-wears/average-days-between-
     * wears/rotation-score, all computed in Kotlin from this one list rather
     * than a second, inconsistent SQL-side definition of "worn." */
    @Query(
        """
        SELECT date FROM (
            SELECT date FROM wear_events WHERE garmentId = :garmentId AND status = 'WORN'
            UNION ALL
            SELECT we.date AS date
            FROM wear_events we
            JOIN outfit_garments og ON og.outfitId = we.outfitId
            WHERE og.garmentId = :garmentId AND we.status = 'WORN'
        )
        ORDER BY date
        """,
    )
    fun observeWearDatesForGarment(garmentId: Long): Flow<List<String>>

    /** Phase 9 — Shopping Gap Analysis's "how many do I own" side, the same
     * shape as [observeActiveGarmentCountBySeason] but grouped by category. */
    @Query(
        """
        SELECT categoryId AS categoryId, COUNT(*) AS garmentCount
        FROM garments
        WHERE status = 'ACTIVE'
        GROUP BY categoryId
        """,
    )
    fun observeActiveGarmentCountByCategory(): Flow<List<CategoryGarmentCountRow>>

    /** Phase 9 — versatility leaderboard: how many distinct saved outfits
     * each garment appears in. */
    @Query(
        """
        SELECT garmentId AS garmentId, COUNT(DISTINCT outfitId) AS outfitCount
        FROM outfit_garments
        GROUP BY garmentId
        """,
    )
    fun observeOutfitAppearanceCountByGarment(): Flow<List<GarmentOutfitCountRow>>

    /** Phase 9 — "favorite combinations": a self-join on `outfit_garments`
     * pairing every two garments that share an outfit, counted once per
     * unordered pair (`a.garmentId < b.garmentId` avoids double-counting
     * (X,Y) and (Y,X) as separate rows). */
    @Query(
        """
        SELECT a.garmentId AS garmentIdA, b.garmentId AS garmentIdB, COUNT(*) AS pairCount
        FROM outfit_garments a
        JOIN outfit_garments b ON a.outfitId = b.outfitId AND a.garmentId < b.garmentId
        GROUP BY a.garmentId, b.garmentId
        ORDER BY pairCount DESC
        LIMIT :limit
        """,
    )
    fun observeTopGarmentPairs(limit: Int): Flow<List<GarmentPairRow>>
}
