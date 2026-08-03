package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow

/** Discoverable even if the user didn't reopen the Add-to-Wardrobe sheet
 * themselves — [count] comes from
 * [com.wardrobe.app.core.domain.repository.ImportQueueRepository.observeIncompleteCount]. */
@Composable
internal fun ResumeImportBanner(
    count: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    ElevatedCard(
        shape = shape,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Resume Import ($count item${if (count == 1) "" else "s"})",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onClick) { Text("Resume") }
        }
    }
}
