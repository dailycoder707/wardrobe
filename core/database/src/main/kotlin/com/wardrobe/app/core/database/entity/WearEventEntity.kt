package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.wear.WearEventStatus

/**
 * `garmentId`/`outfitId` are both nullable and RESTRICT — exactly one populated,
 * enforced at the repository layer (Room/SQLite can't express this XOR as a schema
 * constraint). RESTRICT specifically so a garment/outfit with wear history can never
 * be hard-deleted out from under its own stats — see phase-3-persistence.md.
 *
 * `status` was added Phase 5d for Outfit Scheduling — indexed alongside `date`
 * since Calendar's month view and Wear History's stats both filter on
 * `(date range, status)` together.
 */
@Entity(
    tableName = "wear_events",
    indices = [
        Index("date"),
        Index("garmentId"),
        Index("outfitId"),
        Index("status"),
        Index("syncId", unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WeatherCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["weatherCacheId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = OccasionEntity::class,
            parentColumns = ["id"],
            childColumns = ["occasionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WearEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val garmentId: Long?,
    val outfitId: Long?,
    val weatherCacheId: Long?,
    val occasionId: Long?,
    val note: String?,
    val status: WearEventStatus = WearEventStatus.WORN,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
