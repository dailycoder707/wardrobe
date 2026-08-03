package com.wardrobe.app.feature.stats.story

/** Wardrobe Story's tap-to-navigate target — this module can't depend on
 * `feature:closet`/`feature:outfits` to navigate directly, so the screen
 * itself resolves which nav callback to call. */
sealed interface StoryNavigationTarget {
    data class Garment(
        val garmentId: Long,
    ) : StoryNavigationTarget

    data class Outfit(
        val outfitId: Long,
    ) : StoryNavigationTarget
}

data class StoryCardUiModel(
    val headline: String,
    val navigationTarget: StoryNavigationTarget? = null,
)

data class StoryUiState(
    val isLoading: Boolean = true,
    val cards: List<StoryCardUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()
}
