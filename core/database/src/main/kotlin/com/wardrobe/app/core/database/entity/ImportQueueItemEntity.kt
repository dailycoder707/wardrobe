package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus

/**
 * Device-local only — deliberately no `syncId`/sync registration, the same
 * exclusion precedent as `weather_cache`/`stats_cache`: an in-progress import
 * on one device has no meaning on another. This table is the queue screen's
 * only source of truth (see `ImportQueueRepository`'s resume contract) —
 * "start a new import" and "resume an interrupted one" are the same code
 * path (insert rows, then observe), not two.
 */
@Entity(tableName = "import_queue_items", indices = [Index("status")])
data class ImportQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceFilePath: String,
    val stagingId: String?,
    val status: ImportQueueItemStatus,
    val errorMessage: String?,
    val savedGarmentId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
