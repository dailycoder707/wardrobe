package com.wardrobe.app.feature.settings.profile

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.ai.AiCapability

/** M15 Part 4's Profile/Settings screen. [nameDraft] is the text field's
 * live editable value; [savedName] is what's actually persisted right now —
 * kept distinct (mirrors `GarmentMetadataFormState`'s draft-vs-persisted
 * shape) so a Save only ever takes effect after [nameDraft] passes
 * [validateName], and an invalid draft never silently overwrites
 * [savedName] with something unintended. `null` [savedName] is real,
 * expected day-one state — the screen shows "What's your name?" rather than
 * fabricating a default. */
@Immutable
data class ProfileUiState(
    val isLoading: Boolean = true,
    val savedName: String? = null,
    val nameDraft: String = "",
    val nameError: String? = null,
    val isSavingName: Boolean = false,
    val avatarImageUri: String? = null,
    val isSavingAvatar: Boolean = false,
    val cloudConfiguredCapabilityCount: Int = 0,
    val totalCapabilityCount: Int = AiCapability.entries.size,
    val connectedDeviceName: String? = null,
    val lastSyncAtLabel: String? = null,
    val appVersion: String = "",
)
