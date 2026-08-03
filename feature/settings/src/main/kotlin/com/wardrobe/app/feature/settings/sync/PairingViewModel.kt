package com.wardrobe.app.feature.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.DevicePairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PairingUiState {
    data object Idle : PairingUiState

    data class ShowingQr(
        val pngBytes: ByteArray,
    ) : PairingUiState

    data class Completed(
        val peerDisplayName: String,
    ) : PairingUiState

    data class Failed(
        val message: String,
    ) : PairingUiState
}

@HiltViewModel
class PairingViewModel
    @Inject
    constructor(
        private val repository: DevicePairingRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
        val state: StateFlow<PairingUiState> = mutableState.asStateFlow()

        fun generateQr() {
            viewModelScope.launch {
                val bytes = repository.generatePairingOfferImage()
                mutableState.value = PairingUiState.ShowingQr(bytes)
            }
        }

        fun onQrScanned(scannedText: String) {
            viewModelScope.launch {
                repository
                    .completePairing(scannedText)
                    .onSuccess { mutableState.value = PairingUiState.Completed(it.displayName) }
                    .onFailure {
                        mutableState.value =
                            PairingUiState.Failed(it.message ?: "Couldn't pair with that device")
                    }
            }
        }

        fun cancel() {
            viewModelScope.launch { repository.cancelPairingOffer() }
        }
    }
