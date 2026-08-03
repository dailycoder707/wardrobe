package com.wardrobe.app.feature.capture.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.ImageProcessingProgress
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.model.garment.ImportQueueItem
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.ProcessingStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private val RESUMABLE_STATUSES =
    setOf(ImportQueueItemStatus.PENDING, ImportQueueItemStatus.IMPORTING, ImportQueueItemStatus.REMOVING_BACKGROUND)

/**
 * Drives every not-yet-`READY_FOR_REVIEW` queue row through
 * [ImageRepository.stageImage] **sequentially** (never concurrently — the
 * pipeline decodes a full-resolution bitmap per item, and running several at
 * once risks OOM it isn't written to handle). The queue screen always
 * observes [ImportQueueRepository.observeQueue] directly, so "start a new
 * import" and "resume one interrupted by an app restart or crash" are the
 * same code path — the sequential picker below just keeps finding the next
 * unfinished row, however it got that way.
 */
@HiltViewModel
class GarmentImportQueueViewModel
    @Inject
    constructor(
        private val importQueueRepository: ImportQueueRepository,
        private val imageRepository: ImageRepository,
    ) : ViewModel() {
        val queue: StateFlow<List<ImportQueueItem>> =
            importQueueRepository
                .observeQueue()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        init {
            resumeStaleSavingRows()
            runSequentialStaging()
        }

        /** A row can only be found `SAVING` here if the process died between a
         * metadata submit and its commit finishing — falls back to
         * `READY_FOR_REVIEW` rather than silently losing the item; the user
         * just redoes that one step, not the whole import. Reads directly
         * from the repository (not the derived [queue] `StateFlow`) since
         * `stateIn`'s `WhileSubscribed` sharing only starts collecting once
         * something actually subscribes to [queue] — `.value` could still be
         * the stale initial empty list at this exact point otherwise. */
        private fun resumeStaleSavingRows() {
            viewModelScope.launch {
                importQueueRepository
                    .observeQueue()
                    .first()
                    .filter { it.status == ImportQueueItemStatus.SAVING }
                    .forEach {
                        importQueueRepository.updateItem(
                            it.copy(status = ImportQueueItemStatus.READY_FOR_REVIEW),
                        )
                    }
            }
        }

        private fun runSequentialStaging() {
            viewModelScope.launch {
                queue.collect { items ->
                    val next = items.firstOrNull { it.status in RESUMABLE_STATUSES } ?: return@collect
                    stageOne(next)
                }
            }
        }

        private suspend fun stageOne(item: ImportQueueItem) {
            val stagingId = item.stagingId ?: UUID.randomUUID().toString()
            imageRepository.stageImage(item.sourceFilePath, stagingId).collect { progress ->
                importQueueRepository.updateItem(item.copy(stagingId = stagingId).applyProgress(progress))
            }
        }

        fun onRetry(itemId: Long) {
            viewModelScope.launch {
                val item = queue.value.firstOrNull { it.id == itemId } ?: return@launch
                importQueueRepository.updateItem(
                    item.copy(status = ImportQueueItemStatus.PENDING, errorMessage = null),
                )
            }
        }
    }

private fun ImportQueueItem.applyProgress(progress: ImageProcessingProgress): ImportQueueItem =
    when (progress) {
        is ImageProcessingProgress.InProgress -> {
            val status =
                if (progress.stage == ProcessingStage.REMOVING_BACKGROUND) {
                    ImportQueueItemStatus.REMOVING_BACKGROUND
                } else {
                    ImportQueueItemStatus.IMPORTING
                }
            copy(status = status)
        }

        is ImageProcessingProgress.Completed -> {
            copy(status = ImportQueueItemStatus.READY_FOR_REVIEW)
        }

        is ImageProcessingProgress.Failed -> {
            copy(status = ImportQueueItemStatus.FAILED, errorMessage = progress.reason)
        }
    }
