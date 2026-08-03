package com.wardrobe.app.core.data.repository

import android.graphics.BitmapFactory
import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.entity.BodyMeasurementsEntity
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.BodyReferencePhotoEntity
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.image.hashing.ImageHasher
import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.BodyProfile
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.tryon.pose.BodyAnchorEstimate
import com.wardrobe.app.core.tryon.pose.BodyAnchorEstimator
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * The only place a [BodyMeasurements] row is ever written
 * ([recomputeMeasurements]) — everything else in this feature only reads it.
 * Single-active-profile-per-device (v1) is enforced here, not by the schema
 * (nothing in `BodyProfileEntity` prevents a second row — see its KDoc):
 * [captureBodyPhoto] reuses the existing profile row if one exists rather
 * than ever inserting a second.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyProfileRepositoryImpl
    @Inject
    constructor(
        private val dao: BodyProfileDao,
        private val fileStore: BodyImageFileStore,
        private val bodyAnchorEstimator: BodyAnchorEstimator,
    ) : BodyProfileRepository {
        override fun observeBodyProfile(): Flow<BodyProfile?> =
            dao.observeProfile().flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(null)
                } else {
                    dao.observePhotos(profile.id).map { photos -> profile.toDomain(photos) }
                }
            }

        override fun observeBodyMeasurements(): Flow<BodyMeasurements?> =
            dao.observeProfile().flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(null)
                } else {
                    dao.observeMeasurements(profile.id).map { it?.toDomain() }
                }
            }

        override suspend fun captureBodyPhoto(
            pose: BodyPose,
            sourceFilePath: String,
        ) {
            withContext(Dispatchers.IO) {
                val profile = existingOrNewProfile()
                fileStore.ensureExists(fileStore.profileDir(profile.id))
                val destination = fileStore.photoFile(profile.id, pose)
                File(sourceFilePath).copyTo(destination, overwrite = true)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destination.path, bounds)
                val now = System.currentTimeMillis()
                dao.upsertPhoto(
                    BodyReferencePhotoEntity(
                        bodyProfileId = profile.id,
                        pose = pose.name,
                        filePath = destination.path,
                        width = bounds.outWidth,
                        height = bounds.outHeight,
                        checksum = ImageHasher.sha256(destination),
                    ),
                )
                dao.updateProfile(profile.copy(updatedAt = now))
            }
        }

        override suspend fun deleteBodyProfile() {
            withContext(Dispatchers.IO) {
                val profile = dao.getProfile() ?: return@withContext
                fileStore.deleteProfileDirectory(profile.id)
                dao.deleteProfile(profile.id)
            }
        }

        override suspend fun recomputeMeasurements() {
            withContext(Dispatchers.IO) {
                val profile = dao.getProfile() ?: return@withContext
                val photo =
                    dao.getPhoto(profile.id, BodyPose.ARMS_OUT.name)
                        ?: dao.getPhoto(profile.id, BodyPose.NEUTRAL.name)
                        ?: return@withContext
                val bitmap = BitmapFactory.decodeFile(photo.filePath) ?: return@withContext
                val estimate = bodyAnchorEstimator.estimate(bitmap)
                dao.upsertMeasurements(estimate.toEntity(profile.id))
            }
        }

        private suspend fun existingOrNewProfile(): BodyProfileEntity {
            dao.getProfile()?.let { return it }
            val now = System.currentTimeMillis()
            val newProfile =
                BodyProfileEntity(
                    label = "Me",
                    createdAt = now,
                    updatedAt = now,
                    syncId = UUID.randomUUID().toString(),
                )
            val id = dao.insertProfile(newProfile)
            return requireNotNull(dao.getProfileById(id))
        }
    }

private fun BodyAnchorEstimate.toEntity(bodyProfileId: Long): BodyMeasurementsEntity {
    val now = System.currentTimeMillis()
    return when (this) {
        is BodyAnchorEstimate.Success -> {
            BodyMeasurementsEntity(
                bodyProfileId = bodyProfileId,
                shoulderWidthFraction = shoulderWidthFraction,
                torsoHeightFraction = torsoHeightFraction,
                waistHeightFraction = waistHeightFraction,
                hipWidthFraction = hipWidthFraction,
                neckPositionYFraction = neckPositionYFraction,
                anklePositionYFraction = anklePositionYFraction,
                confidence = confidence,
                source = MeasurementSource.POSE_DETECTION.name,
                computedAt = now,
            )
        }

        is BodyAnchorEstimate.Failure -> {
            BodyMeasurementsEntity(
                bodyProfileId = bodyProfileId,
                shoulderWidthFraction = null,
                torsoHeightFraction = null,
                waistHeightFraction = null,
                hipWidthFraction = null,
                neckPositionYFraction = null,
                anklePositionYFraction = null,
                confidence = null,
                source = MeasurementSource.DEFAULT_HEURISTIC.name,
                computedAt = now,
            )
        }
    }
}
