package com.wardrobe.app.feature.onboarding.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.OnboardingRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/** The real, currently-saved name (if any) — never a fabricated one — so
 * the Finish screen can say "You're ready, Alex." only when the user
 * actually provided a name at some point in the flow (or already had one
 * saved), and "You're ready." otherwise. */
@HiltViewModel
class OnboardingFinishViewModel
    @Inject
    constructor(
        private val personalizationRepository: PersonalizationRepository,
        private val onboardingRepository: OnboardingRepository,
    ) : ViewModel() {
        val displayName: StateFlow<String?> =
            personalizationRepository
                .observe()
                .map { it.displayName?.trim()?.takeUnless { name -> name.isEmpty() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private val didFinishFlow = MutableStateFlow(false)
        val didFinish: StateFlow<Boolean> = didFinishFlow.asStateFlow()

        fun onFinish() {
            viewModelScope.launch {
                onboardingRepository.markComplete()
                didFinishFlow.value = true
            }
        }
    }
