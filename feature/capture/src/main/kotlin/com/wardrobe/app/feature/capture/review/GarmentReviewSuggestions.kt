package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.ConfidenceTier
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100
private val PROVENANCE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.getDefault())

/** Acceptance criteria §2/§3/§6: every detected attribute shown with its
 * confidence tier, an info icon exposing full provenance, and an explicit
 * "Unknown — please choose" / "Not supported" / "N/A" (see
 * [MissingFieldReason]) row for every bindable field nothing suggested at
 * all — never a blank dropdown with no explanation, even when [suggestions]
 * is completely empty.
 *
 * [source] (M23) is the AI source that actually produced [suggestions] —
 * `state.aiProcessingSummary?.source` at the call site — so a field the
 * *current* provider structurally can't detect (on-device Material, say)
 * reads as "Not supported by On-Device AI," not the same "Unknown — please
 * choose" a genuinely-undetected-this-time field gets. `null` (the default)
 * preserves the pre-M23 behavior of treating every missing field as
 * undetected. */
@Composable
internal fun MetadataSuggestionsSection(
    suggestions: List<MetadataSuggestion>,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
    onToggleSuggestion: (MetadataField, String) -> Unit,
    source: AiResultSource? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Detected attributes", style = MaterialTheme.typography.titleMedium)
        suggestions.forEach { suggestion ->
            SuggestionRow(suggestion, form, reference, onToggleSuggestion)
        }
        missingBindableFields(suggestions).forEach { field ->
            MissingFieldRow(field, missingFieldReason(field, form.categoryId, reference.categories, source))
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: MetadataSuggestion,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
    onToggleSuggestion: (MetadataField, String) -> Unit,
) {
    var showProvenance by remember { mutableStateOf(false) }
    val bindable = isBindableField(suggestion.field)
    val applied = bindable && isSuggestionApplied(form, suggestion.field, suggestion.value, reference)
    // M23 (state "D" — AI detected it but reference-data resolution failed):
    // a HIGH-confidence suggestion the model was genuinely sure about should
    // always resolve; if it's still unapplied, the value didn't match any
    // real reference-data row or enum constant, which is worth surfacing
    // distinctly from a MEDIUM/LOW suggestion that's simply awaiting review.
    val resolutionFailed = bindable && !applied && suggestion.confidenceTier == ConfidenceTier.HIGH
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (bindable) {
            WardrobeFilterChip(
                label = "${suggestion.field.displayName()}: ${suggestion.value}",
                selected = applied,
                onClick = { onToggleSuggestion(suggestion.field, suggestion.value) },
            )
        } else {
            Text(
                "${suggestion.field.displayName()}: ${suggestion.value}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.wrapContentWidth(),
            )
        }
        Text(
            suggestion.confidenceTier.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = suggestion.confidenceTier.color(),
        )
        IconButton(onClick = { showProvenance = true }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "Why did AI suggest this?", modifier = Modifier.size(16.dp))
        }
    }
    if (resolutionFailed) {
        Text(
            "Detected, but no matching option found — choose manually",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (showProvenance) {
        ProvenanceDialog(suggestion = suggestion, onDismiss = { showProvenance = false })
    }
}

@Composable
private fun ProvenanceDialog(
    suggestion: MetadataSuggestion,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(suggestion.field.displayName()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Value: ${suggestion.value}")
                Text("Source: ${suggestion.provenance.source.displayName()}")
                Text("Provider: ${suggestion.provenance.provider ?: "On-Device"}")
                suggestion.provenance.model?.let { Text("Model: $it") }
                suggestion.confidenceTier?.let { Text("Confidence tier: ${it.displayName()}") }
                suggestion.confidence?.let { Text("Confidence: ${(it * CONFIDENCE_PERCENT_MULTIPLIER).toInt()}%") }
                suggestion.provenance.promptVersion?.let { Text("Prompt: $it") }
                Text("From cache: ${if (suggestion.provenance.cacheHit) "Yes" else "No"}")
                val generatedAt = suggestion.provenance.generatedAt.atZone(ZoneId.systemDefault())
                Text("Generated: ${PROVENANCE_TIME_FORMAT.format(generatedAt)}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** [reason] (M23) distinguishes three genuinely different situations that
 * used to collapse into one "Unknown — please choose": the field doesn't
 * apply to this item's category ([MissingFieldReason.NOT_APPLICABLE], a
 * quiet "N/A"), the AI provider that ran has no real signal for this field
 * at all ([MissingFieldReason.NOT_SUPPORTED], an informational row pointing
 * at Cloud AI — not a warning, since nothing actually went wrong), or the
 * provider is capable but genuinely didn't detect it this time
 * ([MissingFieldReason.NOT_DETECTED], the original warning-icon "Unknown —
 * please choose"). */
@Composable
private fun MissingFieldRow(
    field: MetadataField,
    reason: MissingFieldReason,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (reason) {
            MissingFieldReason.NOT_APPLICABLE -> {
                Text(
                    "${field.displayName()}: N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            MissingFieldReason.NOT_SUPPORTED -> {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Column {
                    Text(
                        "${field.displayName()}: Not supported by On-Device AI",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Enable Cloud AI in Settings for full detection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            MissingFieldReason.NOT_DETECTED -> {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Column {
                    Text("${field.displayName()}: Unknown", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Please choose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** Every bindable field (`MetadataSuggestionResolver.isBindableField`), in a
 * fixed display order, that nothing in [suggestions] mentioned at all —
 * Part 3's "never a blank dropdown with no explanation" applied to the
 * *full* field set, not just the three original M10 fields. */
private val BINDABLE_FIELDS_DISPLAY_ORDER =
    listOf(
        MetadataField.CATEGORY,
        MetadataField.SUBCATEGORY,
        MetadataField.BRAND,
        MetadataField.PRIMARY_COLOR,
        MetadataField.SECONDARY_COLOR,
        MetadataField.PATTERN,
        MetadataField.MATERIAL,
        MetadataField.FABRIC,
        MetadataField.FIT,
        MetadataField.SLEEVE_LENGTH,
        MetadataField.LENGTH,
        MetadataField.NECKLINE,
        MetadataField.GENDER,
        MetadataField.WATERPROOF_LEVEL,
        MetadataField.SEASON,
        MetadataField.DRESS_CODE,
        MetadataField.OCCASION,
        MetadataField.STYLE_TAG,
    )

private fun missingBindableFields(suggestions: List<MetadataSuggestion>): List<MetadataField> {
    val suggestedFields = suggestions.map { it.field }.toSet()
    return BINDABLE_FIELDS_DISPLAY_ORDER.filterNot { it in suggestedFields }
}

private fun AiResultSource.displayName(): String =
    when (this) {
        AiResultSource.ON_DEVICE -> "On-Device"
        AiResultSource.CLOUD -> "Cloud"
        AiResultSource.MANUAL -> "Manual"
    }

private fun ConfidenceTier?.displayName(): String =
    when (this) {
        ConfidenceTier.HIGH -> "High"
        ConfidenceTier.MEDIUM -> "Medium"
        ConfidenceTier.LOW -> "Low"
        null -> "Unknown"
    }

@Composable
private fun ConfidenceTier?.color() =
    when (this) {
        ConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
        ConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
        ConfidenceTier.LOW, null -> MaterialTheme.colorScheme.outline
    }

private fun MetadataField.displayName(): String =
    name
        .lowercase()
        .split('_')
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
