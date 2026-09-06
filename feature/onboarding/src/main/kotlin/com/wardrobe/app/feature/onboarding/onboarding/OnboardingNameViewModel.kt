package com.wardrobe.app.feature.onboarding.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.profile.NameValidationResult
import com.wardrobe.app.core.domain.profile.errorMessageOrNull
import com.wardrobe.app.core.domain.profile.validateName
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** M16's Name step — the exact same [validateName]/[PersonalizationRepository]
 * the Profile screen (`feature:settings`) uses, not a second name field or a
 * second validation copy. "Skip" simply advances without ever calling
 * [PersonalizationRepository.setDisplayName] — the saved name (if any) stays
 * genuinely unset rather than defaulted to something fabricated. */
@HiltViewModel
class OnboardingNameViewModel
    @Inject
    constructor(
        private val personalizationRepository: PersonalizationRepository,
    ) : ViewModel() {
        private val stateFlow = MutableStateFlow(OnboardingNameUiState())
        val uiState: StateFlow<OnboardingNameUiState> = stateFlow.asStateFlow()

        fun onNameDraftChanged(draft: String) {
            stateFlow.value = stateFlow.value.copy(nameDraft = draft, nameError = null)
        }

        fun onSaveName() {
            when (val result = validateName(stateFlow.value.nameDraft)) {
                is NameValidationResult.Valid -> {
                    stateFlow.value = stateFlow.value.copy(isSaving = true, nameError = null)
                    viewModelScope.launch {
                        personalizationRepository.setDisplayName(result.trimmed)
                        stateFlow.value = stateFlow.value.copy(isSaving = false, didSave = true)
                    }
                }

                else -> {
                    stateFlow.value = stateFlow.value.copy(nameError = result.errorMessageOrNull())
                }
            }
        }
    }

data class OnboardingNameUiState(
    val nameDraft: String = "",
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val didSave: Boolean = false,
)
