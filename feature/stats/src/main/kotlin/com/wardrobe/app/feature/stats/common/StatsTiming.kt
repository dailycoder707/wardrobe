package com.wardrobe.app.feature.stats.common

import com.wardrobe.app.core.ui.debug.StatsDiagnostics

/** Wraps a Kotlin-side derived-computation block (building chart buckets,
 * Wardrobe Story cards, Wardrobe Health advisories) with a timing report
 * into the Developer Panel — see `StatsDiagnostics`'s own KDoc for why this
 * is "derived-computation time," not raw SQL execution time. */
internal inline fun <T> recordTiming(
    label: String,
    block: () -> T,
): T {
    val start = System.nanoTime()
    val result = block()
    val elapsedMillis = (System.nanoTime() - start) / NANOS_PER_MILLI
    StatsDiagnostics.recordQueryTiming(label, elapsedMillis)
    return result
}

private const val NANOS_PER_MILLI = 1_000_000L
