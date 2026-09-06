package com.wardrobe.app.feature.onboarding.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/** Decides `WardrobeNavHost`'s real start destination — `null` while the
 * first read of [OnboardingRepository.observeIsComplete] is still
 * in flight (DataStore + Room, both fast, but never assumed synchronous),
 * then `true`/`false` once known. The app's one `NavHost` composes only
 * after this resolves, so it never flashes Home before redirecting to
 * Onboarding or vice versa. */
@HiltViewModel
class OnboardingGateViewModel
    @Inject
    constructor(
        onboardingRepository: OnboardingRepository,
    ) : ViewModel() {
        val isOnboardingComplete: StateFlow<Boolean?> =
            onboardingRepository
                .observeIsComplete()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    }
