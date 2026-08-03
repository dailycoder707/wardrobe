package com.wardrobe.app.feature.outfits.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.ui.debug.RecommendationDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

@HiltViewModel
class StylistPreferencesViewModel
    @Inject
    constructor(
        private val repository: StylistPreferencesRepository,
    ) : ViewModel() {
        val preferences: StateFlow<RecommendationPreferences> =
            repository
                .observePreferences()
                .onStart { RecommendationDiagnostics.onSubscribed() }
                .onCompletion { RecommendationDiagnostics.onUnsubscribed() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecommendationPreferences())

        fun update(transform: (RecommendationPreferences) -> RecommendationPreferences) {
            viewModelScope.launch { repository.setPreferences(transform(preferences.value)) }
        }
    }
