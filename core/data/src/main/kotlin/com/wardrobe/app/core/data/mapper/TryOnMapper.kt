package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.BodyMeasurementsEntity
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.BodyReferencePhotoEntity
import com.wardrobe.app.core.database.entity.GarmentMaskEntity
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.GarmentPlacementTemplateId
import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.BodyProfile
import com.wardrobe.app.core.model.tryon.BodyReferencePhoto
import com.wardrobe.app.core.model.tryon.GarmentMask
import com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.model.tryon.PlacementTemplateType
import java.time.Instant

internal fun BodyProfileEntity.toDomain(photos: List<BodyReferencePhotoEntity>) =
    BodyProfile(
        id = BodyProfileId(id),
        label = label,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        photos = photos.map { it.toDomain() },
    )

internal fun BodyReferencePhotoEntity.toDomain() =
    BodyReferencePhoto(
        pose = BodyPose.valueOf(pose),
        filePath = filePath,
        width = width,
        height = height,
    )

internal fun BodyMeasurementsEntity.toDomain() =
    BodyMeasurements(
        bodyProfileId = BodyProfileId(bodyProfileId),
        shoulderWidthFraction = shoulderWidthFraction,
        torsoHeightFraction = torsoHeightFraction,
        waistHeightFraction = waistHeightFraction,
        hipWidthFraction = hipWidthFraction,
        neckPositionYFraction = neckPositionYFraction,
        anklePositionYFraction = anklePositionYFraction,
        confidence = confidence,
        source = MeasurementSource.valueOf(source),
        computedAt = Instant.ofEpochMilli(computedAt),
    )

internal fun GarmentPlacementTemplateEntity.toDomain() =
    GarmentPlacementTemplate(
        id = GarmentPlacementTemplateId(id),
        bodyProfileId = BodyProfileId(bodyProfileId),
        garmentId = GarmentId(garmentId),
        templateType = PlacementTemplateType.valueOf(templateType),
        customName = customName,
        offsetXFraction = offsetXFraction,
        offsetYFraction = offsetYFraction,
        scale = scale,
        rotationDegrees = rotationDegrees,
        isUserAdjusted = isUserAdjusted,
        placementSource = MeasurementSource.valueOf(placementSource),
        lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    )

internal fun GarmentMaskEntity.toDomain() =
    GarmentMask(
        bodyProfileId = BodyProfileId(bodyProfileId),
        garmentId = GarmentId(garmentId),
        maskFilePath = maskFilePath,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
