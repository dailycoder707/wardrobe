package com.wardrobe.app.feature.onboarding.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** M16's AI/privacy step — explanation only, no configuration UI of its own
 * (that stays exclusively in `AiProvidersScreen`, `feature:settings`). Never
 * enables Cloud AI or writes any provider config; "Continue with on-device
 * AI" requires no write at all, since on-device is already every
 * capability's real default (`AiProviderConfig.onDeviceDefault`). */
@Composable
fun OnboardingAiScreen(
    onConfigureAi: () -> Unit,
    onContinueOnDevice: () -> Unit,
) {
    OnboardingScaffold(
        title = "How Ladoo uses AI",
        primaryActionLabel = "Continue with on-device AI",
        onPrimaryAction = onContinueOnDevice,
    ) {
        InfoCard(
            title = "On-device AI",
            body =
                "Processing stays on your phone wherever the built-in, on-device models support it — " +
                    "no photo ever leaves your device for these.",
        )
        InfoCard(
            title = "Cloud AI (optional)",
            body =
                "You can connect a cloud provider for richer analysis and styling. This requires " +
                    "configuring a provider and explicitly consenting first — it's never turned on automatically.",
        )
        TextButton(onClick = onConfigureAi, modifier = Modifier.fillMaxWidth()) {
            Text("Configure AI")
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}
