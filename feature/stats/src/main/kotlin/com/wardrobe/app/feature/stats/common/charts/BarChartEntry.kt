package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.runtime.Immutable

@Immutable
data class BarChartEntry(
    val label: String,
    val value: Int,
    val isHighlighted: Boolean = false,
)
