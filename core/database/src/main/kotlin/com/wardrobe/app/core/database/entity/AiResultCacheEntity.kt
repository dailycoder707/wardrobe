package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiResultSource

/**
 * The multi-stage cache *and* provenance ledger (Add-to-Wardrobe v2,
 * ADR-012 §4/§6) — one row per unique
 * `(imageSha256, capability, provider, model, promptVersion)` combination,
 * folded into a single [cacheKey] computed by `core:ai` (not by this
 * entity). A cache hit short-circuits an entire Gateway dispatch — no
 * network call, no job — and this same row is what "why did the app
 * suggest this" / "regenerate with the improved prompt" reads from later.
 * Device-local only: a cached AI result for one device's photo has no
 * meaning to sync to another device.
 */
@Entity(tableName = "ai_result_cache", indices = [Index("cacheKey", unique = true), Index("capability")])
data class AiResultCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cacheKey: String,
    val capability: AiCapability,
    val provider: String?,
    val model: String?,
    val promptVersion: String?,
    val source: AiResultSource,
    val resultPayloadJson: String,
    val confidence: Float?,
    val generatedAt: Long,
)
