package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Performance cache only — never read as a source of truth. See ADR-006. Invalidated
 * (deleted) on the writes that would change its underlying query result, not on a
 * timer — a Phase 5a/6 implementation detail.
 */
@Entity(tableName = "stats_cache")
data class StatsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val jsonValue: String,
    val computedAt: Long,
)
