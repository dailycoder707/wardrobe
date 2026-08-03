package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * `garmentId` is RESTRICT (not CASCADE): a garment that's part of a saved outfit
 * can't be hard-deleted without first being removed from the outfit — a deliberate
 * product rule, see phase-3-persistence.md.
 */
@Entity(
    tableName = "outfit_garments",
    primaryKeys = ["outfitId", "layerSlot"],
    indices = [Index("garmentId")],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class OutfitGarmentCrossRef(
    val outfitId: Long,
    val layerSlot: Int,
    val garmentId: Long,
)
