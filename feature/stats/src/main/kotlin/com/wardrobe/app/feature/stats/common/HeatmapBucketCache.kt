package com.wardrobe.app.feature.stats.common

import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.ui.debug.StatsDiagnostics
import com.wardrobe.app.feature.stats.common.charts.BarChartEntry

internal data class MonthlyWeeklyBuckets(
    val monthly: List<BarChartEntry>,
    val weekly: List<BarChartEntry>,
)

/**
 * The one Kotlin-side cache this module actually needs: bucketing the Usage
 * Heatmap's daily rows into monthly/weekly bars is a real `O(n)` grouping
 * pass, and Room occasionally re-emits an unchanged row list when an
 * unrelated table it also watches changes — a single-slot memo avoids
 * redoing that work for an identical payload. Deliberately not a general-
 * purpose cache: everything else in this module is either a cheap `Flow`
 * pass-through or already Room's own job (ADR-006).
 */
internal object HeatmapBucketCache {
    private var cachedKey: Int? = null
    private var cachedValue: MonthlyWeeklyBuckets? = null

    fun getOrCompute(
        heatmap: List<WearHeatmapDay>,
        compute: () -> MonthlyWeeklyBuckets,
    ): MonthlyWeeklyBuckets {
        val key = heatmap.hashCode()
        val cached = cachedValue
        if (cached != null && cachedKey == key) {
            StatsDiagnostics.recordCacheHit()
            return cached
        }
        val computed = compute()
        cachedKey = key
        cachedValue = computed
        StatsDiagnostics.recordCacheMiss()
        return computed
    }
}
