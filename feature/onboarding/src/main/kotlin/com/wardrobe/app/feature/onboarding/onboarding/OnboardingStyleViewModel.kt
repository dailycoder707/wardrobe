package com.wardrobe.app.feature.onboarding.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/** M16's Style step reuses [StylistPreferencesRepository] directly — the
 * exact repository the real Stylist Preferences screen (`feature:outfits`)
 * and the styling engine's own scoring (`ContextScoring.kt`) already read —
 * rather than a second preferences model. Only a curated subset of
 * [RecommendationPreferences] is surfaced here (see
 * `OnboardingStyleScreen`'s own doc comment for which fields and why); the
 * rest keep their existing defaults untouched, exactly as if the user had
 * never opened the full Stylist Preferences screen at all. "Skip" simply
 * advances without calling [update] even once. */
@HiltViewModel
class OnboardingStyleViewModel
    @Inject
    constructor(
        private val repository: StylistPreferencesRepository,
    ) : ViewModel() {
        val preferences: StateFlow<RecommendationPreferences> =
            repository
                .observePreferences()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecommendationPreferences())

        fun update(transform: (RecommendationPreferences) -> RecommendationPreferences) {
            viewModelScope.launch { repository.setPreferences(transform(preferences.value)) }
        }
    }
