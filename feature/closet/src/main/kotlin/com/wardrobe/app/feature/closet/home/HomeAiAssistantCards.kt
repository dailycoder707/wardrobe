package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.SectionHeader

/** M15 Part 5's Home additions — kept in their own file (mirroring
 * `HomeIntelligenceCards.kt`'s precedent) so `HomeScreen.kt` doesn't grow
 * past detekt's `TooManyFunctions` threshold every time Home gains a new
 * AI-facing reason to change. */
private val HEADER_AVATAR_SIZE = 44.dp

@Composable
internal fun ProfileAvatarButton(
    avatarImageUri: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(HEADER_AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Open your profile" },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarImageUri != null) {
            AsyncImage(
                model = avatarImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** A real, derived nudge — shown only when *no* AI capability is currently
 * Cloud-configured, using the exact same `AiProviderConfig.isCloudReady()`
 * check the AI Providers screen itself uses. Absent entirely once at least
 * one capability is configured, or while the assistant state is still
 * loading (never a flash of "not configured" before the real answer is
 * known). M18 — navigates straight to the authoritative AI Providers screen
 * rather than routing through Profile first; this is the one place Home
 * itself should ever need to reach for the answer to "why isn't cloud AI
 * available." */
@Composable
internal fun CloudAiConfigurationPrompt(
    assistantState: HomeAssistantUiState,
    onOpenAiProviders: () -> Unit,
) {
    if (assistantState.isLoading || assistantState.totalAiCapabilities == 0) return
    if (assistantState.cloudAiConfiguredCount > 0) return
    ElevatedCard(onClick = onOpenAiProviders, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // M22: matches RecommendationPreviewCard's title scale — Home's
            // card titles previously spanned three different type scales
            // with no shared convention.
            Text("Running fully on-device", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connect a cloud AI provider for richer garment analysis and styling. " +
                    "Everything already works on-device — this is optional.",
                style = MaterialTheme.typography.bodySmall,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            Text(
                "Configure Cloud AI",
                style = MaterialTheme.typography.labelLarge,
                color = WardrobeTheme.extendedColors.accent,
            )
        }
    }
}

/** Absent entirely (not an empty placeholder) until the assistant has
 * actually run an AI capability — never a fabricated "no activity yet" row
 * that implies a feature exists when nothing has happened. */
@Composable
internal fun RecentAiActivitySection(activity: List<AiActivityUiModel>) {
    if (activity.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Recent AI Activity")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            activity.forEach { entry -> AiActivityRow(entry) }
        }
    }
}

@Composable
private fun AiActivityRow(entry: AiActivityUiModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(entry.headline, style = MaterialTheme.typography.bodyMedium)
        Text(
            entry.detail,
            style = MaterialTheme.typography.labelSmall,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
    }
}
