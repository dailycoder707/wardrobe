package com.wardrobe.app.feature.onboarding.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Skip" on the Welcome screen skips the *entire* flow — a legitimate,
 * explicit completion (never a fake one): it writes nothing to
 * [com.wardrobe.app.core.domain.repository.PersonalizationRepository] or
 * [com.wardrobe.app.core.domain.repository.StylistPreferencesRepository],
 * it only marks onboarding itself as done so it doesn't show again. Mirrors
 * the `didSave`-flag-plus-`LaunchedEffect` pattern
 * `GarmentReviewMetadataViewModel` already uses for a fire-and-forget action
 * the screen needs to react to (here, navigating to Home) once it lands. */
@HiltViewModel
class OnboardingWelcomeViewModel
    @Inject
    constructor(
        private val onboardingRepository: OnboardingRepository,
    ) : ViewModel() {
        private val didSkipFlow = MutableStateFlow(false)
        val didSkip: StateFlow<Boolean> = didSkipFlow.asStateFlow()

        fun onSkipAll() {
            viewModelScope.launch {
                onboardingRepository.markComplete()
                didSkipFlow.value = true
            }
        }
    }
