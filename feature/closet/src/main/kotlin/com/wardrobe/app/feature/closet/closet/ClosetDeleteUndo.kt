package com.wardrobe.app.feature.closet.closet

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Builds the Delete action wired to [ClosetSelectionController.beginDelete] +
 * the deferred-real-delete Undo flow below — plain (non-Composable) since it
 * only closes over stable references, called once per [ClosetScreen]
 * recomposition rather than needing its own `remember`. */
internal fun buildDeleteSelectedAction(
    viewModel: ClosetViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
): () -> Unit =
    {
        val ids = viewModel.selection.beginDelete()
        scope.launch { runUndoableDelete(viewModel, snackbarHostState, ids.size) }
    }

/** The actual [ClosetSelectionController.confirmPendingDeletion] only runs
 * once this Snackbar's suspend `showSnackbar` call returns without an Undo
 * tap. */
internal suspend fun runUndoableDelete(
    viewModel: ClosetViewModel,
    snackbarHostState: SnackbarHostState,
    deletedCount: Int,
) {
    val result =
        snackbarHostState.showSnackbar(
            message = "Deleted $deletedCount item${if (deletedCount == 1) "" else "s"}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
    if (result == SnackbarResult.ActionPerformed) {
        viewModel.selection.cancelPendingDeletion()
    } else {
        viewModel.selection.confirmPendingDeletion()
    }
}
