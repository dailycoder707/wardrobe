package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A manual erase/restore mask over a garment's own cutout (see
 * `core:model`'s `GarmentMask` KDoc for why it's stored in the cutout's own
 * pixel space, not body-photo space). One per `(bodyProfileId, garmentId)`,
 * shared across every placement template for that garment. Independently
 * sync-tracked, same reasoning as [GarmentPlacementTemplateEntity] — real
 * user effort worth syncing between the user's own paired devices. */
@Entity(
    tableName = "garment_masks",
    indices = [
        Index(value = ["bodyProfileId", "garmentId"], unique = true),
        Index("syncId", unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = BodyProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["bodyProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GarmentMaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyProfileId: Long,
    val garmentId: Long,
    val maskFilePath: String,
    val checksum: String? = null,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
