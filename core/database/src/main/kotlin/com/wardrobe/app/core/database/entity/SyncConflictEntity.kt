package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A surfaced edit/delete conflict (Phase 8) — created only when automatic
 * resolution is genuinely impossible (the same [entitySyncId] was edited on
 * one device and deleted on the other since they last synced). Never created
 * for an ordinary newest-wins field conflict or a collection merge — those
 * resolve deterministically without user input, per Constitution: context/
 * sync must refine, never demand attention for something it can already
 * resolve safely.
 */
@Entity(tableName = "sync_conflict")
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entitySyncId: String,
    val reason: String,
    val localSummary: String,
    val remoteSummary: String,
    val detectedAt: Long,
    val resolvedAt: Long? = null,
    val resolution: String? = null,
)
