package com.wardrobe.app.feature.closet.closet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

/** [onTakePhoto]/[onImportStarted] bagged since a single-callback-per-line
 * signature would push [ClosetScreen]'s parameter count over detekt's
 * threshold once combined with its other navigation/state params. */
data class ClosetAddActions(
    val onTakePhoto: () -> Unit,
    val onImportStarted: () -> Unit,
)

/** Hidden while in multi-select mode, whose own top bar already covers
 * delete/favorite actions — a second FAB there would be redundant. */
@Composable
internal fun ClosetAddFab(
    visible: Boolean,
    onClick: () -> Unit,
) {
    if (visible) {
        FloatingActionButton(onClick = onClick) {
            Icon(Icons.Filled.Add, contentDescription = "Add to Wardrobe")
        }
    }
}
