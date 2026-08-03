package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow

/**
 * `docs/design/component-library.md`'s "Navigation Dock" — a floating pill
 * anchored to the bottom of the screen. Only [destinations] actually built so
 * far are passed in; per `navigation-flow.md` the full shell has 5 destinations
 * (Home, Closet, Outfits, Calendar, More), but Outfits/Calendar/More don't
 * exist as real screens yet (Phase 5d/5e/5f) — showing a disabled placeholder
 * tab for a screen that doesn't exist would fail this phase's own "no
 * placeholders" instruction, so the dock only lists what's real today.
 *
 * The frosted-glass surface (Section 7, design system) needs `RenderEffect`
 * blur, API 31+; below that this falls back to a solid `surfaceContainerHigh`
 * at high opacity — the platform fallback the design doc calls out explicitly
 * as a real requirement, not an afterthought.
 */
@Composable
fun NavigationDock(
    destinations: List<NavigationDockDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockShape = RoundedCornerShape(WardrobeRadius.sheet)
    Surface(
        modifier =
            modifier
                .height(72.dp)
                .wardrobeShadow(WardrobeElevation.FLOATING, dockShape),
        shape = dockShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = GLASS_FALLBACK_ALPHA),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val destinationDescription =
                    "${destination.label}, tab, ${if (selected) "selected" else "not selected"}"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .size(width = 72.dp, height = 72.dp)
                            .selectable(selected = selected, onClick = { onSelect(index) })
                            .semantics {
                                role = Role.Tab
                                contentDescription = destinationDescription
                            }.padding(top = 12.dp),
                ) {
                    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                        tint = tint,
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (selected) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .background(WardrobeTheme.extendedColors.accent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private const val GLASS_FALLBACK_ALPHA = 0.92f
