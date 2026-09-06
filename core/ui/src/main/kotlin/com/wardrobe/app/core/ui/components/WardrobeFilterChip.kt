package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius

private val CHECK_ICON_SIZE = 16.dp
private const val CHECK_ICON_TEST_TAG = "wardrobe_filter_chip_check"

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
    contentDescriptionOverride: String? = null,
) {
    val shape = RoundedCornerShape(WardrobeRadius.chip)
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .semantics {
                    role = Role.Checkbox
                    contentDescription =
                        contentDescriptionOverride ?: "$label, ${if (selected) "selected" else "not selected"}"
                }.height(32.dp)
                .wrapContentWidth()
                .background(background, shape)
                .then(border)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
    ) {
        // M22 fix: selected vs. unselected previously communicated only via
        // background fill/border for sighted users (screen readers already
        // had the real signal via the semantics above) — a checkmark gives
        // low-vision users relying on shape, not subtle color contrast, the
        // same information.
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(CHECK_ICON_SIZE).testTag(CHECK_ICON_TEST_TAG),
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}
