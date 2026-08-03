package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved affine (translate/scale/rotate — never a warp) placement for
 * a garment against a body profile. Several templates can coexist per
 * `(bodyProfileId, garmentId)` — `DEFAULT`/`CASUAL`/`FORMAL` presets plus
 * as many `CUSTOM`-named variants as the user saves — so the unique index
 * covers `templateType`/`customName` too, not just the garment/profile
 * pair. Independently sync-tracked (its own `syncId`/`updatedAt`) since a
 * user's saved placements are real effort worth syncing between their own
 * paired devices, per Constitution rule 13/ADR-011. */
@Entity(
    tableName = "garment_placement_templates",
    indices = [
        Index("bodyProfileId"),
        Index("garmentId"),
        Index(value = ["bodyProfileId", "garmentId", "templateType", "customName"], unique = true),
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
data class GarmentPlacementTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyProfileId: Long,
    val garmentId: Long,
    val templateType: String,
    val customName: String?,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val isUserAdjusted: Boolean,
    val placementSource: String,
    val lastUsedAt: Long?,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
