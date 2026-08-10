package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.ui.components.MultiSelectChips

/** M16's Style step. Deliberately shows only a curated subset of
 * [com.wardrobe.app.core.model.styling.RecommendationPreferences] —
 * [com.wardrobe.app.core.model.styling.RecommendationPreferences.preferredDressCodes],
 * [com.wardrobe.app.core.model.styling.RecommendationPreferences.preferFavorites],
 * and [com.wardrobe.app.core.model.styling.RecommendationPreferences.preferComfortableFit] —
 * confirmed genuinely read by `RecommendationRuleEngine`'s real scoring, not
 * the full ~15-toggle set the standalone Stylist Preferences screen
 * (`feature:outfits`) shows. Every other field keeps its existing default;
 * the full screen remains reachable later from Profile → Style Preferences
 * for anyone who wants to tune the rest. */
@Composable
fun OnboardingStyleScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingStyleViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    OnboardingScaffold(
        title = "What's your style?",
        subtitle = "This helps recommendations feel like you. You can change this anytime from Profile.",
        primaryActionLabel = "Continue",
        onPrimaryAction = onContinue,
        onSkip = onSkip,
    ) {
        MultiSelectChips(
            label = "Dress codes you wear most",
            allOptions = DressCode.entries,
            selected = preferences.preferredDressCodes,
            labelFor = {
                it.name
                    .lowercase()
                    .replace('_', ' ')
                    .replaceFirstChar(Char::uppercase)
            },
            onToggle = { dressCode ->
                viewModel.update { current ->
                    val updated =
                        if (dressCode in current.preferredDressCodes) {
                            current.preferredDressCodes - dressCode
                        } else {
                            current.preferredDressCodes + dressCode
                        }
                    current.copy(preferredDressCodes = updated)
                }
            },
        )
        ToggleRow(
            label = "Prefer my favorites in recommendations",
            checked = preferences.preferFavorites,
            onCheckedChange = { checked -> viewModel.update { it.copy(preferFavorites = checked) } },
        )
        ToggleRow(
            label = "Prioritize comfortable fit",
            checked = preferences.preferComfortableFit,
            onCheckedChange = { checked -> viewModel.update { it.copy(preferComfortableFit = checked) } },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
