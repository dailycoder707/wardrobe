package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class HeatmapCellUiModel(
    val date: LocalDate,
    val wearCount: Int,
)
