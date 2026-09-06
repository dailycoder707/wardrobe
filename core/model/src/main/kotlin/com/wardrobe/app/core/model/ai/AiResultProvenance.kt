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
    /** What the user's Settings actually asked for, which is not always what
     * ran: a configured cloud capability whose dispatch fails degrades to
     * on-device (by design — cloud never breaks a capability), and before
     * this field that degradation was invisible above the router. Defaults
     * to [source], so a result that never attempted anything else is
     * truthfully "requested == actual" and every existing call site keeps
     * its exact meaning. */
    val requestedSource: AiResultSource = source,
    /** Why [requestedSource] didn't produce this result — `null` whenever
     * no fallback happened. Provider-reported text only (already redacted
     * of credentials by the adapter that produced it); never an auth
     * header, key, or raw request payload. */
    val fallbackReason: String? = null,
) {
    /** True only when the pipeline genuinely asked for one source and
     * delivered another — never `true` for a plain on-device run the user
     * selected deliberately. */
    val fallbackUsed: Boolean get() = requestedSource != source
}
