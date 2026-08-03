package com.wardrobe.app.core.database.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.wardrobe.app.core.database.entity.GarmentColorPaletteCrossRef
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentMaterialCrossRef
import com.wardrobe.app.core.database.entity.GarmentSeasonCrossRef
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.database.entity.ImageMetadataEntity

/**
 * The full row-level graph for one garment. Deliberately raw: cross-ref rows carry
 * only foreign-key ids (e.g. `colorId`, not a resolved `ColorEntity`) — resolving
 * those into rich domain objects is a `core:data` mapping concern (Phase 5a), not
 * this module's job. Room resolves each `@Relation` as a separate query per parent
 * row; acceptable at this app's realistic scale (see phase-3-persistence.md's FTS
 * section for the same scale argument applied there).
 */
data class GarmentWithRelations(
    @Embedded val garment: GarmentEntity,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val palette: List<GarmentColorPaletteCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val materials: List<GarmentMaterialCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val tags: List<GarmentTagCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val seasons: List<GarmentSeasonCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val dressCodes: List<GarmentDressCodeCrossRef>,
    @Relation(parentColumn = "id", entityColumn = "garmentId")
    val images: List<ImageMetadataEntity>,
)
