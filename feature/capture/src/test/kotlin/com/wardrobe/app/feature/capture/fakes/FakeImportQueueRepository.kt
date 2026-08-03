package com.wardrobe.app.feature.capture.fakes

import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.model.garment.ImportQueueItem
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeImportQueueRepository : ImportQueueRepository {
    private val mutableQueue = MutableStateFlow<List<ImportQueueItem>>(emptyList())
    private var nextId = 1L

    override suspend fun enqueue(filePaths: List<String>): List<ImportQueueItem> {
        val now = Instant.now()
        val items =
            filePaths.map { path ->
                ImportQueueItem(
                    id = nextId++,
                    sourceFilePath = path,
                    stagingId = null,
                    status = ImportQueueItemStatus.PENDING,
                    errorMessage = null,
                    savedGarmentId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        mutableQueue.value = mutableQueue.value + items
        return items
    }

    override fun observeQueue(): Flow<List<ImportQueueItem>> = mutableQueue.asStateFlow()

    override fun observeIncompleteCount(): Flow<Int> =
        mutableQueue.map { items -> items.count { it.status != ImportQueueItemStatus.COMPLETED } }

    override suspend fun updateItem(item: ImportQueueItem) {
        mutableQueue.value = mutableQueue.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun deleteCompleted() {
        mutableQueue.value = mutableQueue.value.filterNot { it.status == ImportQueueItemStatus.COMPLETED }
    }

    fun currentItems(): List<ImportQueueItem> = mutableQueue.value
}
