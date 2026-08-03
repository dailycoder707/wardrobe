package com.wardrobe.app.feature.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BackupRepository
import com.wardrobe.app.core.domain.repository.SyncPreferencesRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.domain.repository.SyncScheduler
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncPreferences
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/** Bundled purely to keep [SyncViewModel]'s constructor short — the
 * established "bag" pattern (`DeveloperPanelRepositories`, etc.). */
class SyncScreenRepositories
    @Inject
    constructor(
        val syncRepository: SyncRepository,
        val syncPreferencesRepository: SyncPreferencesRepository,
        val syncScheduler: SyncScheduler,
        val backupRepository: BackupRepository,
    )

@HiltViewModel
class SyncViewModel
    @Inject
    constructor(
        private val repositories: SyncScreenRepositories,
    ) : ViewModel() {
        val status: StateFlow<SyncStatusSnapshot> =
            repositories.syncRepository
                .observeStatus()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncStatusSnapshot())

        val conflicts: StateFlow<List<SyncConflict>> =
            repositories.syncRepository
                .observeUnresolvedConflicts()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val history: StateFlow<List<SyncHistoryEntry>> =
            repositories.syncRepository
                .observeHistory()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val preferences: StateFlow<SyncPreferences> =
            repositories.syncPreferencesRepository
                .observePreferences()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncPreferences())

        fun manualSync() {
            repositories.syncScheduler.syncNow()
        }

        fun resolveConflict(
            conflictId: Long,
            resolution: ConflictResolution,
        ) {
            viewModelScope.launch { repositories.syncRepository.resolveConflict(conflictId, resolution) }
        }

        fun updatePreferences(transform: (SyncPreferences) -> SyncPreferences) {
            viewModelScope.launch {
                val updated = transform(preferences.value)
                repositories.syncPreferencesRepository.setPreferences(updated)
                repositories.syncScheduler.reschedule(updated.wifiOnly, updated.chargingOnly)
            }
        }

        fun exportBackup(destinationUri: String) = repositories.backupRepository.exportBackup(destinationUri)

        fun restoreBackup(sourceUri: String) = repositories.backupRepository.restoreBackup(sourceUri)
    }
