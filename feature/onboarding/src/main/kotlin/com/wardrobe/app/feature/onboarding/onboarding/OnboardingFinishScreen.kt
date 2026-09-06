package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingFinishScreen(
    onDone: () -> Unit,
    viewModel: OnboardingFinishViewModel = hiltViewModel(),
) {
    val name by viewModel.displayName.collectAsStateWithLifecycle()
    val didFinish by viewModel.didFinish.collectAsStateWithLifecycle()
    LaunchedEffect(didFinish) { if (didFinish) onDone() }

    OnboardingScaffold(
        title = if (name != null) "You're ready, $name." else "You're ready.",
        subtitle = "Your wardrobe is one tap away.",
        primaryActionLabel = "Go to My Wardrobe",
        onPrimaryAction = viewModel::onFinish,
    )
}
