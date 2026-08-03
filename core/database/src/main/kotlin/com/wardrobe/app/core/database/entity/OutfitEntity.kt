package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.outfit.OutfitSource

@Entity(
    tableName = "outfits",
    indices = [Index("occasionId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = OccasionEntity::class,
            parentColumns = ["id"],
            childColumns = ["occasionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val occasionId: Long?,
    val source: OutfitSource,
    val isSaved: Boolean,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String? = null,
    val mood: String? = null,
    val photoUri: String?,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
