package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.ImportQueueDao
import com.wardrobe.app.core.database.entity.ImportQueueItemEntity
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.model.garment.ImportQueueItem
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ImportQueueRepositoryImpl
    @Inject
    constructor(
        private val dao: ImportQueueDao,
    ) : ImportQueueRepository {
        override suspend fun enqueue(filePaths: List<String>): List<ImportQueueItem> {
            val now = System.currentTimeMillis()
            val entities =
                filePaths.map { path ->
                    ImportQueueItemEntity(
                        sourceFilePath = path,
                        stagingId = null,
                        status = ImportQueueItemStatus.PENDING,
                        errorMessage = null,
                        savedGarmentId = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            val ids = dao.insertAll(entities)
            return entities.zip(ids) { entity, id -> entity.copy(id = id).toDomain() }
        }

        override fun observeQueue(): Flow<List<ImportQueueItem>> =
            dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override fun observeIncompleteCount(): Flow<Int> = dao.observeIncompleteCount()

        override suspend fun updateItem(item: ImportQueueItem) {
            dao.update(item.toEntity().copy(updatedAt = System.currentTimeMillis()))
        }

        override suspend fun deleteCompleted() {
            dao.deleteCompleted()
        }
    }
