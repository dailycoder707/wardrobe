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
    /** M22 fix: `null` means "not resolved yet," distinct from a real `0`
     * (wardrobe genuinely needs no attention). Previously this defaulted to
     * a non-nullable `0`, so [AttentionItemsCard] couldn't tell "still
     * loading" from "loaded, healthy" and silently hid itself in both
     * cases — the same null-means-unknown pattern [wardrobeHealthScore]
     * above already uses correctly. */
    val itemsNeedingAttentionCount: Int? = null,
    val upcomingTripReminder: String? = null,
    val laundryReminderCount: Int = 0,
    /** M15 Part 5 — real rows from `ai_call_log`, newest first; empty (not
     * fabricated placeholder rows) until the assistant has actually run an
     * AI capability at least once. Now collected live (M18), not a one-shot
     * snapshot, so a capability call finishing while Home stays open is
     * reflected without leaving/reopening the screen. */
    val recentAiActivity: List<AiActivityUiModel> = emptyList(),
    /** M18 — a present-tense label for the single, real, currently in-flight
     * `AiJobManager` job (if any), e.g. "Analyzing garment…". `null`
     * whenever nothing is genuinely running — never a fabricated "thinking"
     * state. When more than one job is in flight, the earliest-started one
     * is shown (the one a user is most likely already waiting on). */
    val activeAiOperationLabel: String? = null,
    /** How many of the app's AI capabilities currently have Cloud AI
     * configured+consented (`AiProviderConfig.isCloudReady()`) versus the
     * total capability count — real, derived, never a guess at what the
     * user "probably" wants. */
    val cloudAiConfiguredCount: Int = 0,
    val totalAiCapabilities: Int = 0,
)
