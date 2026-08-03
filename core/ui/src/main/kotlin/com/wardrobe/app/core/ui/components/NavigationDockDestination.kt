package com.wardrobe.app.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class NavigationDockDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)
