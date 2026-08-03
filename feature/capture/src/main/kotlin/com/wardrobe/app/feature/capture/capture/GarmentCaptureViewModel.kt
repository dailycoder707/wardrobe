package com.wardrobe.app.feature.capture.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns only the storage/queue round-trip — the screen owns
 * `CameraCaptureController`/`PreviewView`/CameraX plumbing directly, the same
 * split `feature:tryon`'s `BodyProfileCaptureViewModel`/`-Screen` already
 * established (the hardest-to-automate-here tier, `phase-1-architecture.md`
 * Section 27). */
@HiltViewModel
class GarmentCaptureViewModel
    @Inject
    constructor(
        private val importQueueRepository: ImportQueueRepository,
    ) : ViewModel() {
        private val mutableIsQueued = MutableStateFlow(false)
        val isQueued: StateFlow<Boolean> = mutableIsQueued.asStateFlow()

        /** Called once a photo has already been written to [capturedFilePath]
         * on disk. */
        fun onPhotoCaptured(capturedFilePath: String) {
            viewModelScope.launch {
                importQueueRepository.enqueue(listOf(capturedFilePath))
                mutableIsQueued.value = true
            }
        }
    }
