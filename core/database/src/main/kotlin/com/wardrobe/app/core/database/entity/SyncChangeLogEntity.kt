package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The sync outbox (Phase 8). One row per INSERT/UPDATE/DELETE on any
 * syncable table, written by a database trigger (`MIGRATION_4_5`) — never by
 * a repository-level hook, so a new call site can't silently forget to log
 * a change. [id] (not [changedAt]) is the durable sync cursor: a device
 * remembers the highest `sync_change_log.id` it has already sent to a given
 * peer (`PairedDeviceEntity.lastSyncedChangeLogId`) and queries `id > cursor`
 * next time, which stays correct even if two changes land in the same
 * millisecond. [changedAt] is carried along for display/debugging only.
 */
@Entity(
    tableName = "sync_change_log",
    indices = [Index("tableName", "syncId")],
)
data class SyncChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val syncId: String,
    val operation: String,
    val changedAt: Long,
)
