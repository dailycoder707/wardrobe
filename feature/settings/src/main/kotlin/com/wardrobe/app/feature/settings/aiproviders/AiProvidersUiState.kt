package com.wardrobe.app.feature.settings.aiproviders

import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiUsageSummary

/** Whether [AiProviderRowUiState.testState] reflects a test that's still
 * running, or one that finished with a real (never fabricated) result. */
sealed interface AiConnectionTestState {
    data object Loading : AiConnectionTestState

    data class Done(
        val result: AiConnectionTestResult,
    ) : AiConnectionTestState
}

data class AiProviderRowUiState(
    val capability: AiCapability,
    val config: AiProviderConfig,
    val hasApiKey: Boolean,
    val testState: AiConnectionTestState?,
    val showConsentDialog: Boolean,
)

data class AiProvidersUiState(
    val rows: List<AiProviderRowUiState> = emptyList(),
    val usage: List<AiUsageSummary> = emptyList(),
)
