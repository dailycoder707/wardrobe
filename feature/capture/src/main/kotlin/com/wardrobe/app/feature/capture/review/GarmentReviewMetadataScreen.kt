package com.wardrobe.app.feature.capture.review

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.ai.AiFallbackReasons
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.ui.components.CategoryPicker
import kotlinx.coroutines.delay

private const val SUCCESS_OVERLAY_MS = 550L
private const val RESTAGE_NOTICE_MS = 1200L

/** Bundles the review form's callbacks purely to keep
 * [GarmentReviewMetadataForm] under this project's `LongParameterList`
 * threshold — the same reasoning as `core:ai`'s `AiDispatchContext`. */
private class GarmentReviewCallbacks(
    val onFormChange: (GarmentMetadataFormState) -> Unit,
    val onSave: () -> Unit,
    val onSaveAsDraft: () -> Unit,
    val onToggleSuggestion: (MetadataField, String) -> Unit,
    val onRetryStage: (ImageRetryStage) -> Unit,
    val onCancelAutoSave: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentReviewMetadataScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarmentReviewMetadataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var showRestageNotice by remember { mutableStateOf(false) }

    // M22 fix: this previously navigated away the instant a staged image was
    // lost (e.g. after process death) with zero explanation — the screen just
    // closed. The item safely resumes on its own from the queue either way;
    // this only makes that visible instead of silent.
    LaunchedEffect(state.needsRestage) {
        if (state.needsRestage) {
            showRestageNotice = true
            delay(RESTAGE_NOTICE_MS)
            onDone()
        }
    }
    LaunchedEffect(state.didSave) {
        if (state.didSave) {
            showSuccessOverlay = true
            delay(SUCCESS_OVERLAY_MS)
            onDone()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add Details") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        when {
            showSuccessOverlay -> {
                SavedSuccessOverlay(modifier = Modifier.padding(innerPadding))
            }

            showRestageNotice -> {
                RestageNoticeOverlay(modifier = Modifier.padding(innerPadding))
            }

            state.isLoading -> {
                ReviewSkeleton(modifier = Modifier.padding(innerPadding))
            }

            else -> {
                val callbacks =
                    GarmentReviewCallbacks(
                        onFormChange = viewModel::onFormChange,
                        onSave = viewModel::onSave,
                        onSaveAsDraft = viewModel::onSaveAsDraft,
                        onToggleSuggestion = viewModel::onToggleSuggestion,
                        onRetryStage = viewModel::onRetryStage,
                        onCancelAutoSave = viewModel::onCancelAutoSave,
                    )
                GarmentReviewMetadataForm(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun GarmentReviewMetadataForm(
    state: GarmentReviewMetadataUiState,
    callbacks: GarmentReviewCallbacks,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val reference =
        ReviewReferenceData(
            state.categories,
            state.brands,
            state.colors,
            state.materials,
            state.fabrics,
            state.occasions,
            state.tags,
        )
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GarmentReviewImageViewer(variants = state.variants, comparisonStages = state.comparisonStages)
        ExtractionFallbackBanners(state)

        AiStatusCard(state.aiProcessingSummary)
        WhatAiChangedSummary(state)
        QualityWarningBanner(state.qualityWarnings)
        RetryActionsRow(retryingStage = state.retryingStage, onRetryStage = callbacks.onRetryStage)

        GarmentReviewDuplicateBanners(state)

        state.saveError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        CategoryPicker(
            categories = state.categories,
            selectedCategoryId = form.categoryId,
            onSelect = { callbacks.onFormChange(form.copy(categoryId = it)) },
        )

        val aiSource = state.aiProcessingSummary?.source
        MetadataSuggestionsSection(state.metadataSuggestions, form, reference, callbacks.onToggleSuggestion, aiSource)

        GarmentReviewBasicFields(form, callbacks.onFormChange)
        GarmentReviewDropdowns(state, form, callbacks.onFormChange, reference)
        GarmentReviewGarmentAttributeFields(form, callbacks.onFormChange, state.metadataSuggestions, reference)
        GarmentReviewMultiSelectSections(state, form, callbacks.onFormChange)
        GarmentReviewToggles(form, callbacks.onFormChange)
        val autoSaveSeconds = state.autoSaveCountdownSeconds
        if (autoSaveSeconds != null) {
            AutoSaveBanner(secondsRemaining = autoSaveSeconds, onCancel = callbacks.onCancelAutoSave)
        } else {
            GarmentReviewSaveActions(
                canSave = form.categoryId != null,
                isSaving = state.isSaving,
                onSave = callbacks.onSave,
                onSaveAsDraft = callbacks.onSaveAsDraft,
            )
        }
    }
}

/** Split out of [GarmentReviewMetadataForm] purely to stay under detekt's
 * `LongMethod` threshold (M25) — the two honest "this stage silently
 * degraded" banners: extraction failing entirely ([GarmentReviewMetadataUiState.usedOriginalFallback])
 * vs. extraction succeeding but not via the configured cloud provider
 * ([GarmentReviewMetadataUiState.extractionFallbackReason]) are distinct
 * situations, never conflated. */
@Composable
private fun ExtractionFallbackBanners(state: GarmentReviewMetadataUiState) {
    if (state.usedOriginalFallback) {
        Text(
            "Background removal wasn't able to process this photo — using the original.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    state.extractionFallbackReason?.let { reason ->
        Text(
            extractionFallbackMessage(reason),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Three fallback reasons read as three genuinely different situations, never
 * conflated into one generic "something went wrong":
 * [AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE] is a fixed fact about
 * the configured vendor (no [com.wardrobe.app.core.ai.gateway.ImageTaskAdapter]
 * bound for it, e.g. OpenAI/Claude — Gemini no longer falls in this bucket,
 * M25 Gemini-segmentation follow-up);
 * [AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE] means Gemini was actually
 * asked and answered, but its answer had no usable garment mask; anything
 * else is a transient/technical reason (a timeout, an auth error, a
 * provider's own error text) already worded for direct display by
 * `GeminiErrorMapping`/the router's other failure paths. */
internal fun extractionFallbackMessage(reason: String): String =
    when (reason) {
        AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE -> {
            "Cloud AI doesn't support garment cutout generation for this provider — used On-Device AI instead."
        }

        AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE -> {
            "Gemini garment analysis succeeded, but it couldn't produce a usable cutout — used On-Device AI instead."
        }

        else -> {
            "Cloud AI unavailable for garment extraction — used On-Device AI instead. Reason: $reason"
        }
    }

@Composable
private fun GarmentReviewDuplicateBanners(state: GarmentReviewMetadataUiState) {
    state.checksumDuplicateGarmentName?.let {
        Text(
            "You've already imported this exact photo: \"$it\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.potentialDuplicates.isNotEmpty()) {
        Text(
            "This looks similar to what's already in your wardrobe: " +
                state.potentialDuplicates.joinToString { it.name ?: "an item" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Premium finish (acceptance criterion §10): a shimmering placeholder
 * instead of a bare spinner while the staged image loads. */
@Composable
private fun ReviewSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)

    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerColor),
        )
        repeat(4) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerColor),
            )
        }
    }
}

/** M22 fix: previously the screen just vanished with zero explanation
 * whenever a staged image needed reprocessing (see the `needsRestage`
 * `LaunchedEffect` above) — this makes that moment honest instead of
 * silent. The item genuinely does resume on its own from the queue. */
@Composable
private fun RestageNoticeOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text("This photo needs to be reprocessed", style = MaterialTheme.typography.titleMedium)
        Text(
            "Returning it to the queue…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Premium finish (acceptance criterion §10): a brief success checkmark
 * before handing control back, rather than the screen just vanishing. */
@Composable
private fun SavedSuccessOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Saved",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text("Saved", style = MaterialTheme.typography.titleMedium)
    }
}

internal fun <T> toggled(
    set: Set<T>,
    value: T,
): Set<T> = if (value in set) set - value else set + value
