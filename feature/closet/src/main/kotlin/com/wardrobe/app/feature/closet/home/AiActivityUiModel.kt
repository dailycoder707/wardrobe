package com.wardrobe.app.feature.closet.home

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCapability
import java.time.Duration
import java.time.Instant

/** M15 Part 5's "Recent AI Activity" — a real, already-happened event
 * ([AiActivityEntry]), never a synthesized timeline entry. */
@Immutable
data class AiActivityUiModel(
    val headline: String,
    val detail: String,
)

private fun AiCapability.activityHeadline(): String =
    when (this) {
        AiCapability.GARMENT_EXTRACTION -> "Background removed"
        AiCapability.GARMENT_RECONSTRUCTION -> "Photo reconstructed"
        AiCapability.GARMENT_METADATA -> "Garment analyzed"
        AiCapability.OUTFIT_STYLING -> "Outfit styled"
        AiCapability.VIRTUAL_TRY_ON -> "Try-on generated"
    }

/** M18 — the present-tense counterpart of [activityHeadline] for a
 * genuinely in-flight [com.wardrobe.app.core.model.ai.AiActiveOperation],
 * never shown unless a real job row is `PENDING`/`RUNNING`. */
internal fun AiCapability.inProgressLabel(): String =
    when (this) {
        AiCapability.GARMENT_EXTRACTION -> "Removing background…"
        AiCapability.GARMENT_RECONSTRUCTION -> "Reconstructing photo…"
        AiCapability.GARMENT_METADATA -> "Analyzing garment…"
        AiCapability.OUTFIT_STYLING -> "Styling an outfit…"
        AiCapability.VIRTUAL_TRY_ON -> "Generating try-on…"
    }

internal fun AiActivityEntry.toUiModel(now: Instant): AiActivityUiModel {
    val source = if (cacheHit) "Cached" else provider?.let { "Cloud" } ?: "On-Device"
    return AiActivityUiModel(
        headline = capability.activityHeadline(),
        detail = "$source · ${timeAgoLabel(now)}",
    )
}

private fun AiActivityEntry.timeAgoLabel(now: Instant): String {
    val elapsed = Duration.between(timestamp, now)
    return when {
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()} min ago"
        elapsed.toDays() < 1 -> "${elapsed.toHours()} hour${if (elapsed.toHours() == 1L) "" else "s"} ago"
        else -> "${elapsed.toDays()} day${if (elapsed.toDays() == 1L) "" else "s"} ago"
    }
}
