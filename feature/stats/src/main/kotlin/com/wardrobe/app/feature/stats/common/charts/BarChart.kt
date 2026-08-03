package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

private val CHART_HEIGHT = 120.dp
private val MIN_BAR_HEIGHT = 4.dp

/** A plain vertical bar chart — one question per chart ("which season do I
 * wear the most"), never more than a handful of bars, no axis clutter. The
 * highest bar in [entries] is highlighted in the accent color automatically
 * unless [BarChartEntry.isHighlighted] is set explicitly on specific entries
 * (used for e.g. "today" in a weekly chart). */
@Composable
fun BarChart(
    entries: List<BarChartEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val maxValue = entries.maxOf { it.value }.coerceAtLeast(1)
    val defaultHighlightLabel = entries.maxByOrNull { it.value }?.label

    Row(
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            val highlighted = entry.isHighlighted || entry.label == defaultHighlightLabel
            BarChartColumn(entry, highlighted, maxValue, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun BarChartColumn(
    entry: BarChartEntry,
    highlighted: Boolean,
    maxValue: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = (entry.value.toFloat() / maxValue).coerceIn(0f, 1f)
    val barColor = if (highlighted) WardrobeTheme.extendedColors.accent else MaterialTheme.colorScheme.primary

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(entry.value.toString(), style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .heightIn(min = MIN_BAR_HEIGHT)
                        .clip(barShape)
                        .background(barColor),
            )
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = WardrobeTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
