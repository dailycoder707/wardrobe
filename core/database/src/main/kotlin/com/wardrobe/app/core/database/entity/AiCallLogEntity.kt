package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability

/**
 * The persisted form of every `AiMetrics.record(...)` event (Add-to-
 * Wardrobe v2, ADR-012 §8/§12) — cost tracking is a *consumer* of this
 * stream, not a second write path. Token/cost figures are approximations
 * (this app is vendor-agnostic and owns no vendor's real tokenizer) and are
 * only ever non-null when the user has supplied their own cost-rate
 * estimate — never a fabricated number. Device-local only, never synced,
 * local-only telemetry per ADR-012 §8.
 */
@Entity(tableName = "ai_call_log", indices = [Index("capability"), Index("timestamp")])
data class AiCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capability: AiCapability,
    val provider: String?,
    val model: String?,
    val latencyMs: Long,
    val outcome: AiCallOutcome,
    val confidence: Float?,
    val estimatedInputTokens: Int?,
    val estimatedOutputTokens: Int?,
    val estimatedCostMinorUnits: Long?,
    val cacheHit: Boolean,
    val timestamp: Long,
)
