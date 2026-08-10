package com.wardrobe.app.core.ai.gateway

/** Cents-equivalent minor units per major currency unit — this app is
 * vendor/currency-agnostic, so "minor units" just means "the rate the user
 * entered, times 100" consistently, not a specific currency's real
 * subdivision. */
private const val MINOR_UNITS_PER_MAJOR_UNIT = 100.0
private const val TOKENS_PER_RATE_UNIT = 1000.0

/**
 * Only computes a figure when the user has supplied their own cost-rate
 * estimate for this capability — `null` otherwise, never a fabricated
 * number (Constitution rule 4). Token counts are themselves already
 * approximations by the time they reach here (see each vendor adapter's own
 * disclosure).
 */
internal fun estimateCostMinorUnits(
    costRatePerThousandTokens: Double?,
    inputTokens: Int?,
    outputTokens: Int?,
): Long? {
    if (costRatePerThousandTokens == null) return null
    val totalTokens = (inputTokens ?: 0) + (outputTokens ?: 0)
    return if (totalTokens == 0) {
        null
    } else {
        (costRatePerThousandTokens * totalTokens / TOKENS_PER_RATE_UNIT * MINOR_UNITS_PER_MAJOR_UNIT).toLong()
    }
}
