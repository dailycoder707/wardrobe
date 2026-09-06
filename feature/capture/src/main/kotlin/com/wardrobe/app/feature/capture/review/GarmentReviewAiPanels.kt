package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.garment.AiProcessingSummary
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.QualityCheck
import com.wardrobe.app.core.model.garment.ReconstructionOutcome

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100
private const val MILLIS_PER_SECOND = 1000f

/** Acceptance criterion §7: a concise "AI Processing Complete" summary —
 * only the fields this app can honestly populate are shown; there is no
 * "Cache: —" placeholder standing in for a real measurement (Constitution
 * rule 4). `null` when there's nothing to summarize (extraction failed). */
@Composable
internal fun AiStatusCard(summary: AiProcessingSummary?) {
    if (summary == null) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("AI Processing Complete", style = MaterialTheme.typography.titleSmall)
            }
            Text("Provider: ${summary.provider ?: "On-Device"}", style = MaterialTheme.typography.bodySmall)
            // Only rendered when a cloud attempt genuinely happened and
            // failed — a deliberate On-Device selection never reports a
            // fallback it didn't make. The reason is the provider's own
            // text, already stripped of credentials by the adapter.
            if (summary.fallbackUsed) {
                Text(
                    "Cloud AI unavailable — used On-Device AI instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                summary.fallbackReason?.let { reason ->
                    Text("Reason: $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
            summary.averageConfidence?.let {
                val percent = (it * CONFIDENCE_PERCENT_MULTIPLIER).toInt()
                Text("Confidence: $percent%", style = MaterialTheme.typography.bodySmall)
            }
            val seconds = summary.processingMs / MILLIS_PER_SECOND
            Text("Processing: ${"%.1f".format(seconds)}s", style = MaterialTheme.typography.bodySmall)
            Text("Cache: ${if (summary.cacheHit) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** AI Wardrobe Assistant Part 3 — a real, non-fabricated "what AI changed"
 * summary built directly from what actually ran this import
 * ([GarmentReviewMetadataUiState.comparisonStages]/`reconstructionOutcome`/
 * `metadataSuggestions`, all already produced by the pipeline this state
 * carries) — never a canned "AI improved your photo" string. `null`/empty
 * inputs simply produce fewer bullets, never a fabricated one. */
@Composable
internal fun WhatAiChangedSummary(state: GarmentReviewMetadataUiState) {
    val bullets = buildAiChangeBullets(state)
    if (bullets.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("What AI changed", style = MaterialTheme.typography.titleSmall)
            bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun buildAiChangeBullets(state: GarmentReviewMetadataUiState): List<String> {
    val stageLabels = state.comparisonStages.map { it.label }.toSet()
    return buildList {
        if (ComparisonStageLabel.EXTRACTED in stageLabels && !state.usedOriginalFallback) add("Background removed")
        if (ComparisonStageLabel.EXTRACTED in stageLabels) add("Cutout generated")
        if (ComparisonStageLabel.ENHANCED in stageLabels) add("Presentation enhanced")
        if (state.reconstructionOutcome == ReconstructionOutcome.RECONSTRUCTED) add("Hidden areas reconstructed")
        val detectedFields = state.metadataSuggestions.map { it.field }.distinct()
        if (detectedFields.isNotEmpty()) {
            add("Detected " + detectedFields.joinToString(", ") { it.name.lowercase().replace('_', ' ') })
        }
    }
}

/** Acceptance criterion §8: real, on-device-detected quality issues — never
 * a generic "photo might not be great" — with a retake recommendation. */
@Composable
internal fun QualityWarningBanner(warnings: List<QualityCheck>) {
    if (warnings.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Photo quality issues detected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            warnings.forEach {
                Text(
                    "• ${it.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "Consider retaking this photo for the best result.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Acceptance criterion §9: one-tap retry without recapturing. Disabled
 * while any retry is already in flight, and the in-flight one shows a
 * spinner instead of its label rather than just going dark. */
@Composable
internal fun RetryActionsRow(
    retryingStage: ImageRetryStage?,
    onRetryStage: (ImageRetryStage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetryButton("Retry Extraction", ImageRetryStage.EXTRACTION, retryingStage, onRetryStage)
        RetryButton("Retry Enhancement", ImageRetryStage.ENHANCEMENT, retryingStage, onRetryStage)
        RetryButton("Retry Metadata", ImageRetryStage.METADATA, retryingStage, onRetryStage)
    }
}

@Composable
private fun RetryButton(
    label: String,
    stage: ImageRetryStage,
    retryingStage: ImageRetryStage?,
    onRetryStage: (ImageRetryStage) -> Unit,
) {
    OutlinedButton(onClick = { onRetryStage(stage) }, enabled = retryingStage == null) {
        if (retryingStage == stage) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(label)
        }
    }
}
