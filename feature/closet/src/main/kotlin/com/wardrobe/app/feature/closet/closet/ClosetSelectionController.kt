package com.wardrobe.app.feature.closet.closet

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.model.common.GarmentId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ClosetSelectionState(
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    /** Items hidden from the grid while an Undo Snackbar counts down — the
     * real [GarmentRepository.deleteGarment] call is deliberately delayed
     * until [ClosetSelectionController.confirmPendingDeletion], so tapping
     * Undo ([ClosetSelectionController.cancelPendingDeletion]) never has to
     * un-delete anything, it just clears this set. */
    val pendingDeletionIds: Set<Long> = emptySet(),
)

/** Closet's multi-select mode (Phase 5c) as its own small state machine, so
 * [ClosetViewModel] doesn't have to own both filtering/search/sort *and*
 * selection-mode bookkeeping in one class — keeps it under detekt's
 * function-count budget without a general-purpose use-case layer for logic
 * this specific to one screen. */
class ClosetSelectionController(
    private val garmentRepository: GarmentRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(ClosetSelectionState())
    val state: StateFlow<ClosetSelectionState> = mutableState.asStateFlow()

    fun enter(garmentId: Long) {
        mutableState.value = ClosetSelectionState(isSelectionMode = true, selectedIds = setOf(garmentId))
    }

    fun toggle(garmentId: Long) {
        val current = mutableState.value
        val newSelection =
            if (garmentId in current.selectedIds) current.selectedIds - garmentId else current.selectedIds + garmentId
        mutableState.value =
            ClosetSelectionState(isSelectionMode = newSelection.isNotEmpty(), selectedIds = newSelection)
    }

    fun clear() {
        mutableState.value = ClosetSelectionState()
    }

    /** Hides the selected items from the grid immediately and exits selection
     * mode — the actual delete is deferred to [confirmPendingDeletion]. The
     * caller (the Undo Snackbar's suspend `showSnackbar` call) decides which
     * of [confirmPendingDeletion]/[cancelPendingDeletion] to invoke once the
     * Snackbar is dismissed. Returns the ids being deleted, for the Snackbar
     * message. */
    fun beginDelete(): Set<Long> {
        val ids = mutableState.value.selectedIds
        mutableState.value = ClosetSelectionState(pendingDeletionIds = ids)
        return ids
    }

    fun cancelPendingDeletion() {
        mutableState.value = ClosetSelectionState()
    }

    fun confirmPendingDeletion() {
        scope.launch {
            val ids = mutableState.value.pendingDeletionIds
            var deleted = 0
            var blocked = 0
            ids.forEach { id ->
                runCatching { garmentRepository.deleteGarment(GarmentId(id)) }
                    .onSuccess { deleted++ }
                    .onFailure { blocked++ }
            }
            mutableState.value = ClosetSelectionState()
            onToast(
                when {
                    blocked == 0 -> "Deleted $deleted item${if (deleted == 1) "" else "s"}."
                    deleted == 0 -> "Couldn't delete — items with wear history are protected."
                    else -> "Deleted $deleted item${if (deleted == 1) "" else "s"}, $blocked couldn't be deleted."
                },
            )
        }
    }

    fun favoriteSelected() {
        scope.launch {
            mutableState.value.selectedIds.forEach { id -> garmentRepository.setFavorite(GarmentId(id), true) }
            onToast("Added to favorites.")
            mutableState.value = ClosetSelectionState()
        }
    }
}
