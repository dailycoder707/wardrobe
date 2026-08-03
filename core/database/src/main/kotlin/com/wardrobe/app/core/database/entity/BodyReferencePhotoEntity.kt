package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One of the four guided-capture photos backing a [BodyProfileEntity].
 * Not independently sync-*tracked* (no `syncId`/`updatedAt` of its own,
 * rides along inside its parent profile's `garment_placement`-style change
 * record) but [checksum] still lets its actual image bytes go through the
 * exact same checksum-deduplicated transfer `ImageMetadataSyncHandler`
 * already established for garment photos — see
 * `BodyProfileSyncHandler`/`runImageTransferPhase`. */
@Entity(
    tableName = "body_reference_photos",
    indices = [Index("bodyProfileId")],
    foreignKeys = [
        ForeignKey(
            entity = BodyProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["bodyProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BodyReferencePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyProfileId: Long,
    val pose: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val checksum: String? = null,
)
