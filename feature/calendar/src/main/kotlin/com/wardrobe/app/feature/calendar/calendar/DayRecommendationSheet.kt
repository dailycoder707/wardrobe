package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** M20 Part 6/9/10 — the "recommend for this date" preview/confirm surface,
 * launched from Day Detail's empty state ("Get recommendation") or an
 * existing planned event's "Replace" action. Only ever shown while
 * [state] is not [DayRecommendationUiState.Idle] — the caller gates that. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRecommendationSheet(
    state: DayRecommendationUiState,
    onShowAnother: () -> Unit,
    onPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                DayRecommendationUiState.Idle -> Unit
                DayRecommendationUiState.Loading -> LoadingContent()
                is DayRecommendationUiState.Loaded -> LoadedContent(state, onShowAnother, onPlan, onDismiss)
                is DayRecommendationUiState.Error -> ErrorContent(state.message, onDismiss)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Finding a recommendation…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit,
) {
    Text(message, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onDismiss) { Text("Close") }
}

@Composable
private fun LoadedContent(
    loaded: DayRecommendationUiState.Loaded,
    onShowAnother: () -> Unit,
    onPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val model = loaded.model
    Text(model.title, style = MaterialTheme.typography.titleLarge)
    ThumbnailRow(model.thumbnails)
    RecommendationProvenanceLabel(model)
    Text("Why this?", style = MaterialTheme.typography.labelLarge)
    Text(
        model.explanation,
        style = MaterialTheme.typography.bodyMedium,
        color = WardrobeTheme.extendedColors.textSecondary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPlan) {
            Text(if (loaded.replacingEventId != null) "Replace with this" else "Plan this outfit")
        }
        OutlinedButton(onClick = onShowAnother) { Text("Show another") }
    }
    TextButton(onClick = onDismiss) { Text("Cancel") }
}

@Composable
private fun ThumbnailRow(thumbnails: List<String?>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(thumbnails) { path ->
            val thumbnailModifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
            if (path != null) {
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = thumbnailModifier,
                )
            } else {
                Icon(Icons.Outlined.Checkroom, contentDescription = null, modifier = thumbnailModifier.padding(16.dp))
            }
        }
    }
}

private const val CONFIDENCE_PERCENT_SUFFIX = "%"

/** Mirrors `feature:outfits`' `AiStyledBadge`/`RuleBasedBadge` wording
 * exactly (M18/M19) so the same honest rule-engine-vs-AI distinction reads
 * identically here — not imported directly since feature modules don't
 * cross-depend on one another (ADR-010). */
@Composable
private fun RecommendationProvenanceLabel(model: DayRecommendationUiModel) {
    if (model.providerLabel != null) {
        Column {
            Text(
                "AI Styled",
                style = MaterialTheme.typography.labelLarge,
                color = WardrobeTheme.extendedColors.accent,
            )
            Text(
                text = buildProvenanceDetail(model),
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    } else {
        Text(
            "Styled from your wardrobe preferences and today's context",
            style = MaterialTheme.typography.labelLarge,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
    }
}

private fun buildProvenanceDetail(model: DayRecommendationUiModel): String {
    val confidence = model.confidencePercent?.let { " · Confidence: $it$CONFIDENCE_PERCENT_SUFFIX" }.orEmpty()
    val cacheNote = if (model.isCacheHit) " · From cache" else ""
    return "Provider: ${model.providerLabel}$confidence$cacheNote"
}
