package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

/** Same reasoning as `GarmentSeasonCrossRef` (fixed enum, not a reference table;
 * indexed on [season] alone for the reverse "outfits tagged SUMMER" direction). */
@Entity(
    tableName = "outfit_seasons",
    primaryKeys = ["outfitId", "season"],
    indices = [Index("season")],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OutfitSeasonCrossRef(
    val outfitId: Long,
    val season: Season,
)

/** Same reasoning as [OutfitSeasonCrossRef]. */
@Entity(
    tableName = "outfit_dress_codes",
    primaryKeys = ["outfitId", "dressCode"],
    indices = [Index("dressCode")],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OutfitDressCodeCrossRef(
    val outfitId: Long,
    val dressCode: DressCode,
)

@Entity(
    tableName = "outfit_tags",
    primaryKeys = ["outfitId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
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
data class OutfitTagCrossRef(
    val outfitId: Long,
    val tagId: Long,
)
