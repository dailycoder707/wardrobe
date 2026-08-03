package com.wardrobe.app.feature.closet.closet

internal data class ClosetTopBarActions(
    val onSearchActiveChange: (Boolean) -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSort: () -> Unit,
    val onDeleteSelected: () -> Unit,
)
