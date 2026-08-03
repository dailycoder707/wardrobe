package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

@Entity(
    tableName = "garment_color_palette",
    primaryKeys = ["garmentId", "colorId"],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ColorEntity::class,
            parentColumns = ["id"],
            childColumns = ["colorId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class GarmentColorPaletteCrossRef(
    val garmentId: Long,
    val colorId: Long,
    val weightPercent: Int,
)

@Entity(
    tableName = "garment_materials",
    primaryKeys = ["garmentId", "materialId"],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class GarmentMaterialCrossRef(
    val garmentId: Long,
    val materialId: Long,
    val percentage: Int?,
)

@Entity(
    tableName = "garment_tags",
    primaryKeys = ["garmentId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GarmentTagCrossRef(
    val garmentId: Long,
    val tagId: Long,
)

/**
 * `season` is a fixed Kotlin enum, not a FK to a reference table — refinement #1 in
 * phase-3-persistence.md. The composite primary key covers "seasons for garment X"
 * lookups (garmentId leads); the extra [Index] on `season` alone is what makes
 * Closet Browse's "all garments tagged SUMMER" filter — the reverse direction —
 * actually use an index instead of a full scan.
 */
@Entity(
    tableName = "garment_seasons",
    primaryKeys = ["garmentId", "season"],
    indices = [Index("season")],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GarmentSeasonCrossRef(
    val garmentId: Long,
    val season: Season,
)

/** Same reasoning as [GarmentSeasonCrossRef] — see its KDoc. */
@Entity(
    tableName = "garment_dress_codes",
    primaryKeys = ["garmentId", "dressCode"],
    indices = [Index("dressCode")],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GarmentDressCodeCrossRef(
    val garmentId: Long,
    val dressCode: DressCode,
)
