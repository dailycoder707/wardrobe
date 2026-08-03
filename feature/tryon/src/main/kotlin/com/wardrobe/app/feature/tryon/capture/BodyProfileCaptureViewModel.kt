package com.wardrobe.app.feature.tryon.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.model.tryon.BodyPose
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BodyProfileCaptureUiState(
    val capturedPoses: Set<BodyPose> = emptySet(),
    val currentPose: BodyPose? = BodyPose.entries.first(),
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
)

/**
 * Owns only the guided-sequence state and the storage/DB round-trip
 * (`BodyProfileRepository.captureBodyPhoto`/`recomputeMeasurements`) — the
 * screen owns `BodyCaptureController`/`PreviewView`/CameraX plumbing, the
 * one part of this flow that stays in the hardest-to-automate-here tier
 * `phase-1-architecture.md` Section 27 already named for garment capture.
 */
@HiltViewModel
class BodyProfileCaptureViewModel
    @Inject
    constructor(
        private val bodyProfileRepository: BodyProfileRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(BodyProfileCaptureUiState())
        val uiState: StateFlow<BodyProfileCaptureUiState> = mutableUiState.asStateFlow()

        /** Called once a photo for the *current* pose has already been
         * written to [capturedFilePath] on disk. */
        fun onPhotoCaptured(capturedFilePath: String) {
            val pose = mutableUiState.value.currentPose ?: return
            mutableUiState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                bodyProfileRepository.captureBodyPhoto(pose, capturedFilePath)
                advance(pose)
            }
        }

        /** After every pose is captured, runs [BodyProfileRepository.recomputeMeasurements]
         * exactly once — never per photo, matching the "recompute only when
         * the profile's photos actually change" contract. */
        private suspend fun advance(justCaptured: BodyPose) {
            val captured = mutableUiState.value.capturedPoses + justCaptured
            val remaining = BodyPose.entries.filterNot { it in captured }
            if (remaining.isEmpty()) {
                bodyProfileRepository.recomputeMeasurements()
                mutableUiState.update {
                    it.copy(capturedPoses = captured, currentPose = null, isSaving = false, isComplete = true)
                }
            } else {
                mutableUiState.update {
                    it.copy(capturedPoses = captured, currentPose = remaining.first(), isSaving = false)
                }
            }
        }
    }
