package com.wardrobe.app.core.model.ai

import java.time.Instant

/**
 * Attached to every AI-produced value (Add-to-Wardrobe v2 / ADR-012 §6) —
 * not just a `value` + `confidence` pair. This is what makes "why did the
 * app suggest this" and "regenerate with the improved prompt" answerable
 * later instead of ambiguous. [provider]/[model]/[promptVersion] are `null`
 * for [AiResultSource.ON_DEVICE] and [AiResultSource.MANUAL] results — there
 * is no vendor or prompt to record for those.
 */
data class AiResultProvenance(
    val source: AiResultSource,
    val provider: String?,
    val model: String?,
    val promptVersion: String?,
    val generatedAt: Instant,
    /** Whether this value was served from the Gateway's result cache rather
     * than a fresh provider dispatch — `false` for every on-device result,
     * since there is no cache concept there. Defaulted so every existing
     * on-device call site stays source-compatible. */
    val cacheHit: Boolean = false,
    /** Real, measured wall-clock time for this specific dispatch (or cache
     * lookup) — `null` only for on-device results, which don't route
     * through the Gateway's timing. Never a fabricated/rounded estimate. */
    val latencyMs: Long? = null,
)
