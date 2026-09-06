package com.wardrobe.app.feature.settings.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.profile.NameValidationResult
import com.wardrobe.app.core.domain.profile.errorMessageOrNull
import com.wardrobe.app.core.domain.profile.validateName
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.image.capture.GalleryImportSource
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.feature.settings.di.AppVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private val LAST_SYNC_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/**
 * M15 Part 4 — the Profile/Settings screen's ViewModel. Identity fields
 * (name, avatar) round-trip through [PersonalizationRepository], the same
 * store Home already reads from (see that repository's own KDoc) — nothing
 * here is a second, competing persistence path. [AiProviderSettingsRepository]/
 * [SyncRepository] are read-only here (a compact status line each), never
 * mutated — the AI Providers and Wardrobe Sync screens remain the
 * authoritative place to change either.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val personalizationRepository: PersonalizationRepository,
        private val aiProviderSettingsRepository: AiProviderSettingsRepository,
        private val syncRepository: SyncRepository,
        private val galleryImportSource: GalleryImportSource,
        @AppVersion private val appVersion: String,
    ) : ViewModel() {
        private data class LoadedState(
            val savedName: String?,
            val avatarImageUri: String?,
            val configs: List<AiProviderConfig>,
            val connectedDeviceName: String?,
            val lastSyncAt: Instant?,
        )

        private val loadedStateFlow: StateFlow<LoadedState?> =
            combine(
                personalizationRepository.observe(),
                aiProviderSettingsRepository.observeAllConfigs(),
                syncRepository.observeStatus(),
            ) { personalization, configs, syncStatus ->
                LoadedState(
                    savedName = personalization.displayName?.trim()?.takeUnless { it.isEmpty() },
                    avatarImageUri = personalization.avatarImageUri,
                    configs = configs,
                    connectedDeviceName = syncStatus.connectedDevice?.displayName,
                    lastSyncAt = syncStatus.lastSyncAt,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private data class EditingState(
            val nameDraft: String? = null,
            val nameError: String? = null,
            val isSavingName: Boolean = false,
            val isSavingAvatar: Boolean = false,
        )

        private val editingStateFlow = MutableStateFlow(EditingState())

        val uiState: StateFlow<ProfileUiState> =
            combine(loadedStateFlow, editingStateFlow) { loaded, editing ->
                buildUiState(loaded, editing)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState())

        private fun buildUiState(
            loaded: LoadedState?,
            editing: EditingState,
        ): ProfileUiState {
            if (loaded == null) return ProfileUiState(isLoading = true)
            return ProfileUiState(
                isLoading = false,
                savedName = loaded.savedName,
                nameDraft = editing.nameDraft ?: loaded.savedName.orEmpty(),
                nameError = editing.nameError,
                isSavingName = editing.isSavingName,
                avatarImageUri = loaded.avatarImageUri,
                isSavingAvatar = editing.isSavingAvatar,
                cloudConfiguredCapabilityCount = loaded.configs.count { it.isCloudReady() },
                totalCapabilityCount = loaded.configs.size,
                connectedDeviceName = loaded.connectedDeviceName,
                lastSyncAtLabel = loaded.lastSyncAt?.let(::formatLastSync),
                appVersion = appVersion,
            )
        }

        private fun formatLastSync(instant: Instant): String =
            LAST_SYNC_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

        fun onNameDraftChanged(draft: String) {
            editingStateFlow.value = editingStateFlow.value.copy(nameDraft = draft, nameError = null)
        }

        /** Validates before ever touching persistence — an invalid draft
         * (blank, too long) leaves [PersonalizationRepository]'s saved name
         * completely untouched and surfaces [ProfileUiState.nameError]
         * instead, never a silent fallback to a default name. */
        fun onSaveName() {
            val draft = editingStateFlow.value.nameDraft ?: return
            when (val result = validateName(draft)) {
                is NameValidationResult.Valid -> {
                    editingStateFlow.value = editingStateFlow.value.copy(isSavingName = true, nameError = null)
                    viewModelScope.launch {
                        personalizationRepository.setDisplayName(result.trimmed)
                        editingStateFlow.value = EditingState()
                    }
                }

                else -> {
                    editingStateFlow.value = editingStateFlow.value.copy(nameError = result.errorMessageOrNull())
                }
            }
        }

        /** Copies the picked image into this app's own storage before
         * persisting anything (see [GalleryImportSource.copyAvatarImage]'s
         * KDoc for why the picked [Uri] alone can't be trusted to survive a
         * restart), so "restart the app and still see it" holds for the
         * avatar exactly as it does for the name. */
        fun onAvatarPicked(uri: Uri) {
            editingStateFlow.value = editingStateFlow.value.copy(isSavingAvatar = true)
            viewModelScope.launch {
                val file = withContext(Dispatchers.IO) { galleryImportSource.copyAvatarImage(uri) }
                personalizationRepository.setAvatarImageUri(file.absolutePath)
                editingStateFlow.value = editingStateFlow.value.copy(isSavingAvatar = false)
            }
        }
    }
