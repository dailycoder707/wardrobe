package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

private val VALUE_PROPS =
    listOf(
        "Organize every piece of clothing you own",
        "Understand your wardrobe — colors, materials, categories",
        "Build outfits and get context-aware recommendations",
        "Optionally use cloud AI, only when you choose to",
    )

@Composable
fun OnboardingWelcomeScreen(
    onGetStarted: () -> Unit,
    onSkipped: () -> Unit,
    viewModel: OnboardingWelcomeViewModel = hiltViewModel(),
) {
    val didSkip by viewModel.didSkip.collectAsStateWithLifecycle()
    LaunchedEffect(didSkip) { if (didSkip) onSkipped() }

    OnboardingScaffold(
        title = "Your AI Wardrobe Assistant",
        subtitle = "Ladoo helps you organize, understand, and style what you already own.",
        primaryActionLabel = "Get Started",
        onPrimaryAction = onGetStarted,
        onSkip = viewModel::onSkipAll,
        skipLabel = "Skip setup",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VALUE_PROPS.forEach { line ->
                Text(
                    text = "•  $line",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}
