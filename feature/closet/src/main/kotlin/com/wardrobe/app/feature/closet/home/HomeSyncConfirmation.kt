package com.wardrobe.app.feature.closet.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** Phase 8's "Wardrobe updated just now" — a plain-language line, never a
 * dialog/popup/toast, that fades in when [message] is non-null and fades
 * back out once [HomeViewModel] clears it a few seconds later
 * (Constitution: no technical language, no interruption). Absent entirely
 * (not an empty placeholder) between syncs. */
@Composable
internal fun SyncConfirmationLine(message: String?) {
    AnimatedVisibility(visible = message != null) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}
