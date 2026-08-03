package com.wardrobe.app.feature.stats.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import com.wardrobe.app.core.ui.components.EmptyState

/**
 * Your Wardrobe Story — a handful of plain-language sentences about the year,
 * not a dashboard. Every sentence is a real, derived fact (never fabricated
 * from insufficient data — see `StoryBuilders.kt`'s per-card qualifying
 * conditions), and every card that's *about* a specific garment or look taps
 * through to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    onOpenGarment: (Long) -> Unit,
    onOpenOutfit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Your Wardrobe Story") }) },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.isEmpty -> {
                EmptyState(
                    icon = Icons.Outlined.AutoStories,
                    headline = "Your story is just getting started",
                    supportingText = "Log a few more outfits and check back — this page fills in as you go.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.cards) { card ->
                        StoryCard(
                            card = card,
                            onClick = {
                                when (val target = card.navigationTarget) {
                                    is StoryNavigationTarget.Garment -> onOpenGarment(target.garmentId)
                                    is StoryNavigationTarget.Outfit -> onOpenOutfit(target.outfitId)
                                    null -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    card: StoryCardUiModel,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(WardrobeRadius.heroCard)
    val clickableModifier = if (card.navigationTarget != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        modifier = Modifier.fillMaxWidth().wardrobeShadow(WardrobeElevation.RESTING, shape).then(clickableModifier),
        shape = shape,
        color = WardrobeTheme.extendedColors.accent.copy(alpha = 0.08f),
    ) {
        Text(
            text = card.headline,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(20.dp),
        )
    }
}
