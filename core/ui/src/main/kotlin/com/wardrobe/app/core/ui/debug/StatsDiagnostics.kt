package com.wardrobe.app.core.ui.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StatsDiagnosticsSnapshot(
    val queryTimingsMillis: Map<String, Long> = emptyMap(),
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val activeFlowSubscriptions: Int = 0,
)

/**
 * Phase 5e's Developer Panel window into `feature:stats`' own instrumentation —
 * "derived-computation time" (Kotlin-side aggregation, e.g. bucketing the Usage
 * Heatmap into monthly/weekly views), not raw SQL execution time, which Room
 * doesn't expose per-invocation. Unlike [OutfitBuilderDiagnostics] (one
 * ViewModel, one wholesale-replace `report()`), this has several independent
 * concurrent reporters — the heatmap cache, the Wardrobe Story/Health builders,
 * and every `feature:stats` screen's own subscribe/unsubscribe lifecycle — so
 * each field is updated individually via [MutableStateFlow.update], which is
 * atomic under concurrent callers, rather than one full-snapshot replace that
 * could silently drop a concurrent update from a different reporter.
 */
object StatsDiagnostics {
    private val mutableState = MutableStateFlow(StatsDiagnosticsSnapshot())
    val state: StateFlow<StatsDiagnosticsSnapshot> = mutableState.asStateFlow()

    fun recordQueryTiming(
        label: String,
        millis: Long,
    ) {
        mutableState.update { it.copy(queryTimingsMillis = it.queryTimingsMillis + (label to millis)) }
    }

    fun recordCacheHit() {
        mutableState.update { it.copy(cacheHits = it.cacheHits + 1) }
    }

    fun recordCacheMiss() {
        mutableState.update { it.copy(cacheMisses = it.cacheMisses + 1) }
    }

    fun onSubscribed() {
        mutableState.update { it.copy(activeFlowSubscriptions = it.activeFlowSubscriptions + 1) }
    }

    fun onUnsubscribed() {
        mutableState.update {
            it.copy(activeFlowSubscriptions = (it.activeFlowSubscriptions - 1).coerceAtLeast(0))
        }
    }

    fun reset() {
        mutableState.value = StatsDiagnosticsSnapshot()
    }
}
