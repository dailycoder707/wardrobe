package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One completed (or abandoned) sync session — backs the Wardrobe Sync
 * screen's "Sync History" list and the Developer Panel's diagnostics. */
@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val outcome: String,
    val changesSent: Int,
    val changesReceived: Int,
    val bytesSent: Long,
    val bytesReceived: Long,
    val conflictsDetected: Int,
    val errorMessage: String? = null,
)
