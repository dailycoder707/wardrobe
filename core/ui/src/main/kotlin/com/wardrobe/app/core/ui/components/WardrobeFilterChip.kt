package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius

/** `docs/design/component-library.md`'s "Filter Chip / Tag Chip" — an 8dp
 * rounded rectangle, never a full pill. The confidence-chip (dashed border,
 * AI-suggested) variant belongs to the capture/edit flow's attribute review,
 * not this phase's filter/sort chips. */
@Composable
fun WardrobeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WardrobeRadius.chip)
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .semantics {
                    role = Role.Checkbox
                    contentDescription = "$label, ${if (selected) "selected" else "not selected"}"
                }.height(32.dp)
                .wrapContentWidth()
                .background(background, shape)
                .then(border)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}
