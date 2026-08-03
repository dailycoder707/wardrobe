package com.wardrobe.app.core.ui.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OutfitBuilderDiagnosticsSnapshot(
    val isOpen: Boolean = false,
    val outfitId: Long? = null,
    val filledSlotCount: Int = 0,
    val totalSlotCount: Int = 0,
    val undoStackSize: Int = 0,
    val redoStackSize: Int = 0,
)

/**
 * A narrow side-channel `feature:outfits`' `OutfitBuilderViewModel` reports its
 * own live, in-memory state (undo/redo stacks, which never touch Room) into —
 * read by `feature:closet`'s Developer Panel. Lives in `core:ui`, not either
 * feature module, for the same reason `RecompositionTracker` does: every
 * feature module already depends on `core:ui`, so this is the one place both
 * a reporter (`feature:outfits`) and a reader (`feature:closet`'s Developer
 * Panel) can reach without one feature module depending on another.
 */
object OutfitBuilderDiagnostics {
    private val mutableState = MutableStateFlow(OutfitBuilderDiagnosticsSnapshot())
    val state: StateFlow<OutfitBuilderDiagnosticsSnapshot> = mutableState.asStateFlow()

    fun report(snapshot: OutfitBuilderDiagnosticsSnapshot) {
        mutableState.value = snapshot
    }
}
