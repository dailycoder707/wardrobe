package com.wardrobe.app.feature.tryon.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.ai.AiResultSource

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100

/**
 * M12's Try-On Review comparison viewer: Original / On-Device / Cloud tabs,
 * each showing what that path actually rendered — never a claimed result
 * that wasn't really produced. Preview only; nothing here can overwrite the
 * garment's own cutout or the body profile's photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnCompareScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TryOnCompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Compare Renders") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.missingProfileOrGarment -> {
                    Text(
                        "Set up your try-on profile and make sure this garment has a cutout image first.",
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> {
                    TryOnCompareContent(state, viewModel::selectTab)
                }
            }
        }
    }
}

@Composable
internal fun TryOnCompareContent(
    state: TryOnCompareUiState,
    onSelectTab: (TryOnCompareTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TryOnCompareTab.entries.forEach { tab ->
                TextButton(onClick = { onSelectTab(tab) }) {
                    Text(
                        tab.label(),
                        style =
                            if (tab == state.selectedTab) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                    )
                }
            }
        }
        when (state.selectedTab) {
            TryOnCompareTab.ORIGINAL -> OriginalTabContent(state)
            TryOnCompareTab.ON_DEVICE -> RenderTabContent(state.onDevice)
            TryOnCompareTab.CLOUD -> RenderTabContent(state.cloud)
        }
    }
}

@Composable
private fun OriginalTabContent(state: TryOnCompareUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(
            model = state.bodyPhotoPath,
            contentDescription = "Your original photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        AsyncImage(
            model = state.garmentCutoutPath,
            contentDescription = "Garment cutout",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
    }
}

@Composable
private fun RenderTabContent(tabState: TryOnRenderTabState) {
    when (tabState) {
        is TryOnRenderTabState.Loading -> {
            CircularProgressIndicator()
        }

        is TryOnRenderTabState.Failed -> {
            Text("Couldn't render: ${tabState.reason}")
        }

        is TryOnRenderTabState.Rendered -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AsyncImage(
                    model = tabState.imagePath,
                    contentDescription = "Rendered try-on",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text("Source: ${tabState.source.label()}", style = MaterialTheme.typography.labelMedium)
                tabState.confidence?.let {
                    Text(
                        "Confidence: ${(it * CONFIDENCE_PERCENT_MULTIPLIER).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun TryOnCompareTab.label(): String =
    when (this) {
        TryOnCompareTab.ORIGINAL -> "Original"
        TryOnCompareTab.ON_DEVICE -> "On-Device"
        TryOnCompareTab.CLOUD -> "Cloud"
    }

private fun AiResultSource.label(): String =
    when (this) {
        AiResultSource.ON_DEVICE -> "On-Device"
        AiResultSource.CLOUD -> "Cloud"
        AiResultSource.MANUAL -> "Manual"
    }
