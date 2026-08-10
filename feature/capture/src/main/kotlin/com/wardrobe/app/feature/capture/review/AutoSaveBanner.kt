package com.wardrobe.app.feature.capture.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * AI Wardrobe Assistant Part 2 — shown in place of the normal Save/Save as
 * Draft actions once every required field has reached the confidence bar
 * ([AutoSaveGate.evaluateAutoSaveEligibility] returned `Eligible`). Reuses
 * this screen's existing [SavedSuccessOverlay]-style card treatment
 * (checkmark icon, primary-tinted surface) for visual consistency rather
 * than inventing a new visual language for this one moment.
 *
 * [secondsRemaining] is always in `1..3` while visible — the ViewModel
 * clears it (and the review screen falls back to the ordinary manual save
 * actions) the instant it reaches 0, since by then [onCancel] no longer
 * makes sense to offer.
 */
@Composable
internal fun AutoSaveBanner(
    secondsRemaining: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "AI finished analyzing",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                "Saving in $secondsRemaining…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
