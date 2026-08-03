package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Derived body proportions for a [BodyProfileEntity] — kept in its own
 * table (rather than inline columns on the profile row) so "recompute only
 * when the profile's photos change" is a structural fact: this row is only
 * ever written by `BodyProfileRepository.recomputeMeasurements()`, never
 * per render. One row per `bodyProfileId` (unique). Rides along inside its
 * parent body profile's sync payload, like [BodyReferencePhotoEntity] —
 * no `syncId` of its own. */
@Entity(
    tableName = "body_measurements",
    indices = [Index("bodyProfileId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = BodyProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["bodyProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BodyMeasurementsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyProfileId: Long,
    val shoulderWidthFraction: Float?,
    val torsoHeightFraction: Float?,
    val waistHeightFraction: Float?,
    val hipWidthFraction: Float?,
    val neckPositionYFraction: Float?,
    val anklePositionYFraction: Float?,
    val confidence: Float?,
    val source: String,
    val computedAt: Long,
)
