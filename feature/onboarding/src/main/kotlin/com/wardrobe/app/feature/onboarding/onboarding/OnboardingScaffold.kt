package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** The one shared shell every onboarding screen uses — same title/subtitle/
 * content/primary-button/optional-skip shape, so the five screens read as
 * one calm flow rather than five differently-styled pages. Reuses this
 * app's existing type scale/color tokens exclusively (no new visual
 * language, per M16's UI-quality requirement). Many optional, named params
 * is this project's own documented Compose exception — see `HomeScreen.kt`'s
 * identical suppression. */
@Suppress("LongParameterList")
@Composable
internal fun OnboardingScaffold(
    title: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionEnabled: Boolean = true,
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "Skip",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.displayMedium)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrimaryAction,
                enabled = primaryActionEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryActionLabel)
            }
            if (onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(skipLabel, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
