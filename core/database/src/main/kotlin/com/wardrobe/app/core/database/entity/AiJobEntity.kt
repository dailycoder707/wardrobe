package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus

/**
 * `AiJobManager`'s generalized, capability-agnostic job ledger (Add-to-
 * Wardrobe v2, ADR-012) — one row per dispatched capability call, across
 * all five [AiCapability] values, not just garment import. Device-local
 * only, same exclusion precedent as `import_queue_items`/`weather_cache`: an
 * in-flight AI job on one device has no meaning on another.
 * `ImportQueueItemEntity` stays the capture-flow-specific "what garment
 * photos are mid-import" tracker; its stage transitions call into
 * `AiJobManager` (backed by this table) rather than duplicating job state.
 */
@Entity(tableName = "ai_jobs", indices = [Index("status"), Index("cacheKey")])
data class AiJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capability: AiCapability,
    val cacheKey: String,
    val status: AiJobStatus,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
