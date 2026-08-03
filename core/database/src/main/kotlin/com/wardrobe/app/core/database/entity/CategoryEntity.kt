package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.garment.CategoryLevel

@Entity(
    tableName = "categories",
    indices = [Index("parentId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long?,
    val level: CategoryLevel,
    val syncId: String = "",
    val updatedAt: Long = 0,
)
