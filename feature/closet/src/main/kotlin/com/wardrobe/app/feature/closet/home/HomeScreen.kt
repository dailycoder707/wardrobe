package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.GarmentTile
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import com.wardrobe.app.core.ui.components.SectionHeader
import com.wardrobe.app.feature.closet.addwardrobe.AddToWardrobeSheet

@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    onOpenGarment: (Long) -> Unit,
    onBrowseCloset: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenTrips: () -> Unit,
    onTryOnRecommendation: (List<Long>) -> Unit,
    onTakePhoto: () -> Unit,
    onImportStarted: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDeveloperPanel: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    var isAddSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { isAddSheetOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add to Wardrobe")
            }
        },
    ) { innerPadding ->
        HomeScreenBody(
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
            onAddFirstItem = { isAddSheetOpen = true },
            onResumeImport = onImportStarted,
            onOpenDeveloperPanel = onOpenDeveloperPanel,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (isAddSheetOpen) {
        AddToWardrobeSheet(
            onDismiss = { isAddSheetOpen = false },
            onTakePhoto = onTakePhoto,
            onImportStarted = onImportStarted,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun HomeScreenBody(
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
                onOpenDeveloperPanel = onOpenDeveloperPanel,
                modifier = modifier,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun HomeContent(
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
    onResumeImport: () -> Unit,
    onOpenDeveloperPanel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        HomeHeaderBlock(state = state, onOpenDeveloperPanel = onOpenDeveloperPanel)

        if (state.incompleteImportCount > 0) {
            ResumeImportBanner(count = state.incompleteImportCount, onClick = onResumeImport)
        }

        SyncConfirmationLine(assistantState.syncConfirmationMessage)

        assistantState.weather?.let { WeatherLine(it) }
        TodaysOccasionLine(assistantState.todaysOccasionName)

        QuickActionsRow(onBrowseCloset = onBrowseCloset, onOpenFavorites = onOpenFavorites, onOpenSearch = onOpenSearch)

        assistantState.recommendation?.let { recommendation ->
            RecommendationPreviewCard(
                recommendation = recommendation,
                onOpenGarment = onOpenGarment,
                onOpenRecommendations = onOpenRecommendations,
                onTryOn = { onTryOnRecommendation(recommendation.items.map { it.id }) },
            )
        }

        if (state.showWardrobeHealthCard && state.summary != null) {
            WardrobeSummaryCard(state.summary)
        }

        WardrobeHealthScoreCard(
            healthScore = assistantState.wardrobeHealthScore,
            rotationScore = assistantState.rotationScore,
            onClick = onOpenInsights,
        )
        AttentionItemsCard(count = assistantState.itemsNeedingAttentionCount, onClick = onOpenInsights)
        UpcomingTripReminderLine(reminder = assistantState.upcomingTripReminder, onClick = onOpenTrips)
        LaundryReminderLine(count = assistantState.laundryReminderCount)

        HomeInsightsSection(insights = state.insights, onOpenGarment = onOpenGarment, onOpenInsights = onOpenInsights)

        if (state.continueEditing.isNotEmpty()) {
            GarmentSection(title = "Continue Editing", garments = state.continueEditing, onOpenGarment = onOpenGarment)
        }

        if (state.recentlyAdded.isNotEmpty()) {
            GarmentSection(
                title = "Recently Added",
                garments = state.recentlyAdded,
                onOpenGarment = onOpenGarment,
                trailingActionLabel = "See all",
                onTrailingAction = onBrowseCloset,
            )
        }

        if (state.recentlyWorn.isNotEmpty()) {
            GarmentSection(title = "Recently Worn", garments = state.recentlyWorn, onOpenGarment = onOpenGarment)
        }
    }
}

@Composable
private fun ResumeImportBanner(
    count: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    ElevatedCard(
        shape = shape,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Resume Import ($count item${if (count == 1) "" else "s"})", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onClick) { Text("Resume") }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onBrowseCloset: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBrowseCloset) { Text("Browse Closet") }
        OutlinedButton(onClick = onOpenFavorites) { Text("Favorites") }
        OutlinedButton(onClick = onOpenSearch) { Text("Search") }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeHeaderBlock(
    state: HomeUiState,
    onOpenDeveloperPanel: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.showGreeting && state.greeting.isNotBlank()) {
            Text(text = state.greeting, style = MaterialTheme.typography.displayLarge)
        } else {
            Text(text = state.homeTitle, style = MaterialTheme.typography.displayLarge)
        }
        Text(
            text = state.currentDateText,
            style = MaterialTheme.typography.bodyMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
            // A long-press here is the Developer Panel's only entry point
            // (debug builds only, `onOpenDeveloperPanel` is null in
            // release — see WardrobeNavHost) — deliberately unlabeled so
            // it never reads as a discoverable feature to the app's one
            // real user.
            modifier =
                if (onOpenDeveloperPanel != null) {
                    Modifier
                        .combinedClickable(onClick = {}, onLongClick = onOpenDeveloperPanel)
                        .semantics { contentDescription = state.currentDateText }
                } else {
                    Modifier
                },
        )
    }
}

/** "22°C today. Light rain expected this afternoon." plus the offline-
 * friendly "Updated 2 hours ago" caption — Phase 7's personal-assistant
 * greeting line. Absent entirely (not an error state) when Weather Settings
 * has weather turned off or no weather has ever been resolved yet. */
@Composable
private fun WeatherLine(weather: WeatherCardUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = weather.headline, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = weather.updatedAtLabel,
            style = MaterialTheme.typography.labelSmall,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
    }
}

/** A compact "Recommended Outfit" preview — tapping through opens the full
 * Recommendations screen (`feature:outfits`) where Wear Today/Save/Favorite/
 * Replace actually live; this card is a glance, not a second copy of that
 * state machine. */
@Composable
internal fun RecommendationPreviewCard(
    recommendation: RecommendationPreviewUiModel,
    onOpenGarment: (Long) -> Unit,
    onOpenRecommendations: () -> Unit,
    onTryOn: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    ElevatedCard(
        shape = shape,
        onClick = onOpenRecommendations,
        modifier = Modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recommended Outfit", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recommendation.items, key = { it.id }) { garment ->
                    GarmentTile(
                        garment = garment,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onOpenGarment(garment.id) },
                        onLongClick = {},
                        onFavoriteClick = {},
                        modifier = Modifier.size(RECOMMENDATION_TILE_SIZE),
                    )
                }
            }
            Text(
                text = "Why this outfit? ${recommendation.explanation}",
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            OutlinedButton(onClick = onTryOn) { Text("Try On Me") }
        }
    }
}

@Composable
private fun WardrobeSummaryCard(summary: WardrobeSummaryUiModel) {
    val shape = RoundedCornerShape(20.dp)
    ElevatedCard(
        shape = shape,
        modifier = Modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryStat(value = summary.totalActiveGarments.toString(), label = "In your closet")
            SummaryStat(value = "${summary.usagePercent}%", label = "Worn this month")
            SummaryStat(value = summary.wornAtLeastOnce.toString(), label = "Items worn")
        }
    }
}

@Composable
private fun SummaryStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
    }
}

@Composable
private fun GarmentSection(
    title: String,
    garments: List<GarmentTileUiModel>,
    onOpenGarment: (Long) -> Unit,
    trailingActionLabel: String? = null,
    onTrailingAction: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title, actionLabel = trailingActionLabel, onAction = onTrailingAction)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(garments, key = { it.id }) { garment ->
                GarmentTile(
                    garment = garment,
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = { onOpenGarment(garment.id) },
                    onLongClick = {},
                    onFavoriteClick = {},
                    modifier = Modifier.size(HOME_TILE_SIZE),
                )
            }
        }
    }
}

/** Home Insights (Phase 5e) — a highlight reel, not a second Insights screen.
 * Each chip is either about one specific garment (taps straight to it) or a
 * style summary (taps through to the full Insights screen for depth) — see
 * `phase-5e-wardrobe-intelligence.md`'s design decision on why Home doesn't
 * duplicate every Insights bullet point itself. */
@Composable
private fun HomeInsightsSection(
    insights: HomeInsightsUiModel,
    onOpenGarment: (Long) -> Unit,
    onOpenInsights: () -> Unit,
) {
    if (insights.isEmpty) return
    val chips = buildInsightChips(insights, onOpenGarment, onOpenInsights)
    if (chips.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Your Style", actionLabel = "See all", onAction = onOpenInsights)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(chips, key = { it.label }) { chip -> InsightChip(chip) }
        }
    }
}

private val HOME_TILE_SIZE = 140.dp
private val RECOMMENDATION_TILE_SIZE = 100.dp
