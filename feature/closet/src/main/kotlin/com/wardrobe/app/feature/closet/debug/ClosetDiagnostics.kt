package com.wardrobe.app.feature.closet.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ClosetDiagnosticsSnapshot(
    val searchQuery: String = "",
    val activeFilterCount: Int = 0,
    val filterSummary: String = "none",
    val sortSummary: String = "",
    val resultCount: Int = 0,
)

/**
 * A debug-only diagnostic side-channel — [com.wardrobe.app.feature.closet.closet.ClosetViewModel]
 * reports its own live filter/search/sort state here (a couple of lines in
 * its existing state-building code), and the Developer Panel reads it. This
 * exists only so the debug panel can show *real* Closet state without every
 * screen depending on the Developer Panel's own module, not a general-purpose
 * event bus — release builds never read this (the panel's own destination
 * isn't registered in a release build's nav graph — see `WardrobeNavHost`).
 */
@Singleton
class ClosetDiagnostics
    @Inject
    constructor() {
        private val stateFlow = MutableStateFlow(ClosetDiagnosticsSnapshot())
        val state: StateFlow<ClosetDiagnosticsSnapshot> = stateFlow.asStateFlow()

        fun report(snapshot: ClosetDiagnosticsSnapshot) {
            stateFlow.value = snapshot
        }
    }
