package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wardrobe.app.core.ui.components.EmptyState

/** A top-level (not [HomeScreen]-nested) function purely to keep
 * `HomeScreen.kt`'s own file under detekt's `TooManyFunctions` threshold. */
@Suppress("LongParameterList")
@Composable
internal fun HomeScreenBody(
    state: HomeUiState,
    assistantState: HomeAssistantUiState,
    onOpenGarment: (Long) -> Unit,
    onBrowseCloset: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenTrips: () -> Unit,
    onTryOnRecommendation: (List<Long>) -> Unit,
    onAddFirstItem: () -> Unit,
    onResumeImport: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenDeveloperPanel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            EmptyState(
                icon = Icons.Outlined.SentimentSatisfied,
                headline = "Something went wrong",
                supportingText = state.error.orEmpty(),
                modifier = modifier,
            )
        }

        state.isEmpty -> {
            EmptyState(
                icon = Icons.Filled.Checkroom,
                headline = "Your wardrobe is waiting",
                supportingText = "Add your first garment to start building your closet.",
                actionLabel = "Add your first item",
                onAction = onAddFirstItem,
                modifier = modifier,
            )
        }

        else -> {
            HomeContent(
                state = state,
                assistantState = assistantState,
                onOpenGarment = onOpenGarment,
                onBrowseCloset = onBrowseCloset,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = onOpenSearch,
                onOpenInsights = onOpenInsights,
                onOpenRecommendations = onOpenRecommendations,
                onOpenTrips = onOpenTrips,
                onTryOnRecommendation = onTryOnRecommendation,
                onResumeImport = onResumeImport,
                onOpenProfile = onOpenProfile,
                onOpenAiProviders = onOpenAiProviders,
                onOpenDeveloperPanel = onOpenDeveloperPanel,
                modifier = modifier,
            )
        }
    }
}
