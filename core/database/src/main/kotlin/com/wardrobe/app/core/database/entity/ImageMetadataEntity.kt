package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.garment.ImageType

@Entity(
    tableName = "image_metadata",
    indices = [Index("garmentId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ImageMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garmentId: Long,
    val type: ImageType,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: String,
    val checksum: String?,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
