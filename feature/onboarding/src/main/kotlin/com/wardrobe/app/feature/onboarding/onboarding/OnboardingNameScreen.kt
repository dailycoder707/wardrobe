package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingNameScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingNameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.didSave) { if (state.didSave) onContinue() }

    OnboardingScaffold(
        title = "What should I call you?",
        subtitle = "Your name shows up on Home and anywhere Ladoo addresses you personally.",
        primaryActionLabel = if (state.isSaving) "Saving…" else "Continue",
        primaryActionEnabled = !state.isSaving,
        onPrimaryAction = viewModel::onSaveName,
        onSkip = onSkip,
    ) {
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = viewModel::onNameDraftChanged,
            label = { Text("Your name") },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = { state.nameError?.let { Text(it) } },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
