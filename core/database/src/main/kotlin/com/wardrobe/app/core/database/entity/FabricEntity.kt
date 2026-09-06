package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Weave/construction reference data (Denim, Jersey, Twill...) — mirrors
 * [MaterialEntity] exactly; see [com.wardrobe.app.core.model.garment.Fabric]'s
 * KDoc for why this is a separate table from `materials` rather than a
 * field on it. */
@Entity(tableName = "fabrics", indices = [Index("name", unique = true), Index("syncId", unique = true)])
data class FabricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val syncId: String = "",
    val updatedAt: Long = 0,
)
