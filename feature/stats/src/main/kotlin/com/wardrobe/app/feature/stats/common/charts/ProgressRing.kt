package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

private const val RING_START_ANGLE = -90f
private const val FULL_SWEEP_DEGREES = 360f
private val DEFAULT_RING_SIZE = 96.dp
private val DEFAULT_STROKE_WIDTH = 10.dp

/** Answers one question at a glance — "what fraction of X is done" — for
 * Usage %, cost-per-wear coverage, or any other single-number-out-of-a-whole
 * stat. [progress] is 0f..1f; values outside that range are coerced rather
 * than crashing on a stray rounding error upstream. */
@Composable
fun ProgressRing(
    progress: Float,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_RING_SIZE,
    strokeWidth: Dp = DEFAULT_STROKE_WIDTH,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = WardrobeTheme.extendedColors.accent

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.toPx() - stroke.width, size.toPx() - stroke.width)
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = FULL_SWEEP_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = progressColor,
                startAngle = RING_START_ANGLE,
                sweepAngle = FULL_SWEEP_DEGREES * clampedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Text(text = centerLabel, style = MaterialTheme.typography.titleMedium)
    }
}
