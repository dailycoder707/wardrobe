package com.wardrobe.app.feature.closet.home

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

/** The Home screen's "personal assistant" weather line (Phase 7) — a
 * compact headline plus the offline-friendly "Updated 2 hours ago"/
 * "Updated yesterday"/"No weather available" label every screen showing
 * weather uses (`WeatherSnapshot.updatedAtLabel`, `core:model`). */
@Immutable
data class WeatherCardUiModel(
    val headline: String,
    val updatedAtLabel: String,
)

/** A compact preview of the top recommendation — not the full Recommendations
 * screen's Quick Actions, deliberately: tapping through to
 * `RecommendationsRoute` (`feature:outfits`) is where Wear Today/Save/
 * Favorite/Replace actually live, so this card isn't a second copy of that
 * state machine. */
@Immutable
data class RecommendationPreviewUiModel(
    val items: List<GarmentTileUiModel>,
    val explanation: String,
)

@Immutable
data class HomeAssistantUiState(
    val isLoading: Boolean = true,
    val weather: WeatherCardUiModel? = null,
    val recommendation: RecommendationPreviewUiModel? = null,
    /** Phase 8 — "Wardrobe updated just now," shown briefly whenever a
     * background sync completes. Never a dialog/popup/technical message
     * (Constitution) — just this one plain-language line, auto-dismissed by
     * [HomeViewModel] a few seconds after it appears. */
    val syncConfirmationMessage: String? = null,
    /** Phase 9 — the Daily Wardrobe Brief's remaining pieces beyond weather/
     * recommendation (both already above): today's planned occasion (if
     * any), the composite Wardrobe Health/Rotation scores, a plain count of
     * items needing attention (forgotten + overused + never-worn, capped at
     * a glance, not itemized here), an upcoming-trip reminder sentence, and
     * how many items are currently marked in the laundry — every one
     * tappable, none a popup. */
    val todaysOccasionName: String? = null,
    val wardrobeHealthScore: Int? = null,
    val rotationScore: Int? = null,
    val itemsNeedingAttentionCount: Int = 0,
    val upcomingTripReminder: String? = null,
    val laundryReminderCount: Int = 0,
)
