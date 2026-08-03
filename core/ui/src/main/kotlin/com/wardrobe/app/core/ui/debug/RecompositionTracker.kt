package com.wardrobe.app.core.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A real (not simulated) per-composable recomposition counter, called via
 * [SideEffect] from a handful of the app's hottest composables (the garment
 * grid tile). The debug-only Developer Panel (`feature:closet`) reads
 * [snapshot] so its "Compose recomposition counters" reflects actual counts,
 * never a placeholder number. Living here (`core:ui`) rather than in
 * `feature:closet` is what lets shared composables like [GarmentTile]
 * instrument themselves without a feature module depending back on core:ui.
 */
object RecompositionTracker {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Composable
    fun track(label: String) {
        SideEffect { counts.getOrPut(label) { AtomicInteger(0) }.incrementAndGet() }
    }

    fun snapshot(): Map<String, Int> = counts.mapValues { it.value.get() }

    fun reset() {
        counts.clear()
    }
}
