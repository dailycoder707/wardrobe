package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.database.dao.GarmentMaskDao
import com.wardrobe.app.core.database.entity.GarmentMaskEntity
import com.wardrobe.app.core.domain.repository.GarmentMaskRepository
import com.wardrobe.app.core.image.hashing.ImageHasher
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.tryon.GarmentMask
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class GarmentMaskRepositoryImpl
    @Inject
    constructor(
        private val dao: GarmentMaskDao,
        private val fileStore: BodyImageFileStore,
    ) : GarmentMaskRepository {
        override fun observeGarmentMask(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
        ): Flow<GarmentMask?> = dao.observe(bodyProfileId.value, garmentId.value).map { it?.toDomain() }

        /** [mask].maskFilePath is treated as the *source* mask bitmap to
         * adopt — moved into [BodyImageFileStore.maskFile]'s own path, the
         * same "caller stages a file, the repository takes ownership of the
         * permanent location" contract [BodyProfileRepositoryImpl] uses for
         * reference photos. */
        override suspend fun saveGarmentMask(mask: GarmentMask) {
            withContext(Dispatchers.IO) {
                fileStore.ensureExists(fileStore.masksDir(mask.bodyProfileId.value))
                val destination = fileStore.maskFile(mask.bodyProfileId.value, mask.garmentId.value)
                val source = File(mask.maskFilePath)
                if (source.path != destination.path) {
                    source.copyTo(destination, overwrite = true)
                }
                val existingSyncId = dao.observe(mask.bodyProfileId.value, mask.garmentId.value).first()?.syncId
                dao.upsert(
                    GarmentMaskEntity(
                        bodyProfileId = mask.bodyProfileId.value,
                        garmentId = mask.garmentId.value,
                        maskFilePath = destination.path,
                        checksum = ImageHasher.sha256(destination),
                        updatedAt = System.currentTimeMillis(),
                        syncId = existingSyncId ?: UUID.randomUUID().toString(),
                    ),
                )
            }
        }

        override suspend fun clearGarmentMask(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
        ) {
            withContext(Dispatchers.IO) {
                fileStore.maskFile(bodyProfileId.value, garmentId.value).delete()
                dao.clear(bodyProfileId.value, garmentId.value)
            }
        }
    }
