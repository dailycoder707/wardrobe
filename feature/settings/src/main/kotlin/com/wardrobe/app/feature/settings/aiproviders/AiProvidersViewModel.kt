package com.wardrobe.app.feature.settings.aiproviders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.model.ai.defaultBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

/**
 * The Settings screen's ViewModel for Add-to-Wardrobe v2's five AI
 * capabilities (ADR-012). Every mutation round-trips through
 * [AiProviderSettingsRepository] — this class holds no provider state of
 * its own beyond the ephemeral per-row UI concerns (test-in-flight,
 * consent-dialog-open) that don't belong in persisted config.
 */
@HiltViewModel
class AiProvidersViewModel
    @Inject
    constructor(
        private val repository: AiProviderSettingsRepository,
    ) : ViewModel() {
        private val hasApiKeyFlow = MutableStateFlow<Map<AiCapability, Boolean>>(emptyMap())
        private val testStateFlow = MutableStateFlow<Map<AiCapability, AiConnectionTestState>>(emptyMap())
        private val consentDialogCapabilityFlow = MutableStateFlow<AiCapability?>(null)

        val uiState: StateFlow<AiProvidersUiState> =
            combine(
                repository.observeAllConfigs(),
                repository.observeUsageSummaries(),
                hasApiKeyFlow,
                testStateFlow,
                consentDialogCapabilityFlow,
            ) { configs, usage, hasApiKeyMap, testStates, consentCapability ->
                AiProvidersUiState(
                    rows =
                        configs.map { config ->
                            AiProviderRowUiState(
                                capability = config.capability,
                                config = config,
                                hasApiKey = hasApiKeyMap[config.capability] == true,
                                testState = testStates[config.capability],
                                showConsentDialog = consentCapability == config.capability,
                            )
                        },
                    usage = usage,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AiProvidersUiState())

        init {
            viewModelScope.launch { hasApiKeyFlow.value = loadApiKeyPresence(repository) }
        }

        fun onModeSelected(
            capability: AiCapability,
            mode: AiProviderMode,
        ) {
            viewModelScope.launch {
                val current = repository.observeConfig(capability).first()
                if (mode == AiProviderMode.ON_DEVICE) {
                    repository.setConfig(current.revertToOnDevice())
                } else {
                    repository.setConfig(current.copy(mode = AiProviderMode.CLOUD))
                    consentDialogCapabilityFlow.value = capability
                }
            }
        }

        /** M24 real-device finding — picking a vendor with a real, fixed
         * public API root now pre-fills the Base URL field, so cloud never
         * silently stays unreachable just because that field was left
         * blank. Never overwrites a URL the user already typed (switching
         * vendors while pointed at a shared custom proxy, say, must not
         * clobber it) — only fills in when [AiProviderConfig.baseUrl] is
         * currently blank. See [defaultBaseUrl]'s KDoc for which vendors
         * have no safe default. */
        fun onVendorSelected(
            capability: AiCapability,
            vendor: AiVendor,
        ) = viewModelScope.launch {
            updateConfig(repository, capability) { current ->
                val baseUrl = current.baseUrl.takeUnless { it.isNullOrBlank() } ?: vendor.defaultBaseUrl()
                current.copy(vendor = vendor, baseUrl = baseUrl)
            }
        }

        /** Changing the Base URL to a host that doesn't match the currently
         * consented one invalidates that consent — never silently reused for
         * a different destination (see [AiProviderConfig]'s own KDoc). */
        fun onBaseUrlChanged(
            capability: AiCapability,
            baseUrl: String,
        ) = viewModelScope.launch {
            updateConfig(repository, capability) { current ->
                if (current.consentHost == baseUrl) {
                    current.copy(baseUrl = baseUrl)
                } else {
                    current.copy(baseUrl = baseUrl, consentGrantedAt = null, consentHost = null)
                }
            }
        }

        fun onModelChanged(
            capability: AiCapability,
            model: String,
        ) = viewModelScope.launch {
            updateConfig(repository, capability) { it.copy(model = model.ifBlank { null }) }
        }

        fun onCostRateChanged(
            capability: AiCapability,
            rateText: String,
        ) = viewModelScope.launch {
            updateConfig(repository, capability) { it.copy(costRatePerThousandTokens = rateText.toDoubleOrNull()) }
        }

        fun onRequestConsent(capability: AiCapability) {
            consentDialogCapabilityFlow.value = capability
        }

        fun onConsentGranted() {
            val capability = consentDialogCapabilityFlow.value ?: return
            viewModelScope.launch {
                val current = repository.observeConfig(capability).first()
                repository.setConfig(
                    current.copy(consentGrantedAt = Instant.now(), consentHost = current.baseUrl),
                )
                consentDialogCapabilityFlow.value = null
            }
        }

        fun onConsentDeclined() {
            val capability = consentDialogCapabilityFlow.value ?: return
            viewModelScope.launch {
                val current = repository.observeConfig(capability).first()
                repository.setConfig(current.revertToOnDevice())
                consentDialogCapabilityFlow.value = null
            }
        }

        fun onApiKeyChanged(
            capability: AiCapability,
            apiKey: String,
        ) {
            viewModelScope.launch {
                if (apiKey.isBlank()) repository.clearApiKey(capability) else repository.setApiKey(capability, apiKey)
                hasApiKeyFlow.value = loadApiKeyPresence(repository)
            }
        }

        fun onTestConnection(capability: AiCapability) {
            viewModelScope.launch {
                testStateFlow.update { it + (capability to AiConnectionTestState.Loading) }
                val result = repository.testConnection(capability)
                testStateFlow.update { it + (capability to AiConnectionTestState.Done(result)) }
            }
        }
    }

private suspend fun updateConfig(
    repository: AiProviderSettingsRepository,
    capability: AiCapability,
    transform: (AiProviderConfig) -> AiProviderConfig,
) {
    val current = repository.observeConfig(capability).first()
    repository.setConfig(transform(current))
}

private suspend fun loadApiKeyPresence(repository: AiProviderSettingsRepository): Map<AiCapability, Boolean> =
    AiCapability.entries.associateWith { repository.hasApiKey(it) }

private fun AiProviderConfig.revertToOnDevice(): AiProviderConfig =
    copy(mode = AiProviderMode.ON_DEVICE, consentGrantedAt = null, consentHost = null)
