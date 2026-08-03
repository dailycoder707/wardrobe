package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Exactly one of `garmentId`/`freeTextName` populated in practice — same
 * exactly-one-of-two pattern as `wear_events`, enforced at the app layer. `garmentId`
 * is SET_NULL (not RESTRICT): if the suggested garment is later deleted, the packing
 * list line falls back to a free-text placeholder rather than the delete being
 * blocked or the line disappearing — see phase-3-persistence.md.
 */
@Entity(
    tableName = "packing_list_items",
    indices = [Index("tripId"), Index("garmentId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PackingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val garmentId: Long?,
    val freeTextName: String?,
    val category: String?,
    val isPacked: Boolean,
    val rationale: String?,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
