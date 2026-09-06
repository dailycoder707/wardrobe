package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** M12's "the user always decides" invariant made visible: a cloud-styled
 * outfit is clearly labeled distinct from a rule-engine one, and its full
 * provider/confidence/reasoning sit behind an info button rather than
 * cluttering the card — nothing here changes what Save/Wear Today/Favorite
 * do, this is purely a provenance disclosure. */
@Composable
internal fun AiStyledBadge(model: RecommendedOutfitUiModel) {
    var showInfo by remember(model) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "AI Styled",
            style = MaterialTheme.typography.labelLarge,
            color = WardrobeTheme.extendedColors.accent,
        )
        IconButton(onClick = { showInfo = true }) {
            Icon(Icons.Filled.Info, contentDescription = "Why did AI suggest this outfit?")
        }
    }
    if (showInfo) {
        AiStyledInfoDialog(model = model, onDismiss = { showInfo = false })
    }
}

/** M18's honest counterpart to [AiStyledBadge] — shown for the default,
 * rule-engine-scored case (`provenance == null`) so it's never ambiguous
 * whether an outfit came from an actual AI model or from
 * `RecommendationRuleEngine`'s scoring rules. Reuses the exact same
 * [selected.explanation][RecommendedOutfitUiModel.explanation] text already
 * shown above it — no separate/invented copy. */
@Composable
internal fun RuleBasedBadge() {
    Text(
        "Styled from your wardrobe preferences and today's context",
        style = MaterialTheme.typography.labelLarge,
        color = WardrobeTheme.extendedColors.textSecondary,
    )
}

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100

@Composable
private fun AiStyledInfoDialog(
    model: RecommendedOutfitUiModel,
    onDismiss: () -> Unit,
) {
    val provenance = model.provenance
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Styled") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Provider: ${provenance?.provider ?: "On-Device"}")
                model.aiConfidence?.let {
                    Text("Confidence: ${(it * CONFIDENCE_PERCENT_MULTIPLIER).toInt()}%")
                }
                if (provenance?.cacheHit == true) {
                    Text("Result loaded from cache")
                }
                Text("Reason: ${model.explanation}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
