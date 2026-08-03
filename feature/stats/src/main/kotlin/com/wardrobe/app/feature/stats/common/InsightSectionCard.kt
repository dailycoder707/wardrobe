package com.wardrobe.app.feature.stats.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow

/** The one card shell every Insights/Wardrobe Story/Wardrobe Health section
 * uses — a title, an optional one-line explanation of what question the
 * section answers, and its content. Kept this plain deliberately: this
 * module's whole brief is "answer one question per chart," not compete for
 * visual attention. */
@Composable
fun InsightSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(WardrobeRadius.heroCard)
    Surface(
        modifier = modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }
            content()
        }
    }
}
