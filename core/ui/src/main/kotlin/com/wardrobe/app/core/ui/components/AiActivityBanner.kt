package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/**
 * A compact, reusable "AI is alive" line — one leading icon (or spinner)
 * conveying [tone], the exact real-state [label] the caller supplies, and an
 * optional single action (e.g. "Retry", "Configure Cloud AI"). Never
 * self-generates copy: every string shown is passed in by a screen that
 * already knows the real state (M18 Part 8 — no fabricated AI activity).
 *
 * The label announces itself via [LiveRegionMode.Polite] since callers swap
 * it in place (e.g. "Analyzing garment…" → "AI analysis complete") without
 * necessarily recomposing the whole screen — this is this codebase's first
 * `liveRegion` usage, a deliberate new accessibility precedent for dynamic
 * AI-status text (see ADR-017).
 */
@Composable
fun AiActivityBanner(
    label: String,
    tone: AiActivityTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val containerColor = tone.containerColor()
    val contentColor = tone.contentColor()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor, RoundedCornerShape(WardrobeRadius.chip))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneIcon(tone, contentColor)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ToneIcon(
    tone: AiActivityTone,
    contentColor: Color,
) {
    when (tone) {
        AiActivityTone.RUNNING -> CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        AiActivityTone.SUCCESS -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = contentColor)
        AiActivityTone.FAILED -> Icon(Icons.Filled.Warning, contentDescription = null, tint = contentColor)
        AiActivityTone.INFO -> Icon(Icons.Filled.Info, contentDescription = null, tint = contentColor)
    }
}

@Composable
private fun AiActivityTone.containerColor(): Color =
    when (this) {
        AiActivityTone.RUNNING -> WardrobeTheme.extendedColors.accent.copy(alpha = 0.12f)
        AiActivityTone.SUCCESS -> WardrobeTheme.extendedColors.success.copy(alpha = 0.12f)
        AiActivityTone.FAILED -> MaterialTheme.colorScheme.errorContainer
        AiActivityTone.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
private fun AiActivityTone.contentColor(): Color =
    when (this) {
        AiActivityTone.RUNNING -> WardrobeTheme.extendedColors.accent
        AiActivityTone.SUCCESS -> WardrobeTheme.extendedColors.success
        AiActivityTone.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        AiActivityTone.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
