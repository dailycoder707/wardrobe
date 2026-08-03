package com.wardrobe.app.core.database.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.wardrobe.app.core.database.entity.OutfitDressCodeCrossRef
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.OutfitSeasonCrossRef
import com.wardrobe.app.core.database.entity.OutfitTagCrossRef

/** The full row-level graph for one outfit — same "raw cross-ref rows, resolve in
 * `core:data`" contract as [GarmentWithRelations]. */
data class OutfitWithRelations(
    @Embedded val outfit: OutfitEntity,
    @Relation(parentColumn = "id", entityColumn = "outfitId")
    val garments: List<OutfitGarmentCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "outfitId")
    val seasons: List<OutfitSeasonCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "outfitId")
    val dressCodes: List<OutfitDressCodeCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "outfitId")
    val tags: List<OutfitTagCrossRef>,
)
