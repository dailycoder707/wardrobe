package com.wardrobe.app.core.model.ai

/**
 * The result of the Settings screen's "Test Connection" action (ADR-012 §
 * Settings UI) — always a real, live dispatch through the configured
 * vendor, never a claimed-working state inferred from the config alone
 * (Constitution rule 4).
 */
sealed interface AiConnectionTestResult {
    data class Success(
        val latencyMs: Long,
    ) : AiConnectionTestResult

    data class Failure(
        val reason: String,
    ) : AiConnectionTestResult
}
