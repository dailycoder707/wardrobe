package com.wardrobe.app.feature.settings.aiproviders

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val CAPABILITY = AiCapability.GARMENT_METADATA

/** [AiProvidersViewModel.uiState] is `stateIn`-backed with a seed
 * [AiProvidersUiState] (empty rows) — a fresh subscriber always sees that
 * seed as its first item, then the real combine of the (already-hot) fake
 * repository's flows as its second, same gotcha `WeatherSettingsViewModelTest`
 * documents for `WeatherSettingsViewModel.preferences`. */
private suspend fun ReceiveTurbine<AiProvidersUiState>.awaitRealInitialState(): AiProvidersUiState {
    awaitItem()
    return awaitItem()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AiProvidersViewModelTest {
    private lateinit var repository: FakeAiProviderSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeAiProviderSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `every capability starts on-device`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                val state = awaitRealInitialState()
                assertEquals(AiCapability.entries.size, state.rows.size)
                assertTrue(state.rows.all { it.config.mode == AiProviderMode.ON_DEVICE })
            }
        }

    @Test
    fun `selecting Cloud mode opens the consent dialog for that row and persists CLOUD immediately`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onModeSelected(CAPABILITY, AiProviderMode.CLOUD)
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertEquals(AiProviderMode.CLOUD, row.config.mode)
                assertTrue(row.showConsentDialog)
            }
        }

    @Test
    fun `declining consent reverts the capability to on-device`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onModeSelected(CAPABILITY, AiProviderMode.CLOUD)
                awaitItem()
                viewModel.onConsentDeclined()
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertEquals(AiProviderMode.ON_DEVICE, row.config.mode)
                assertNull(row.config.consentGrantedAt)
            }
        }

    @Test
    fun `granting consent stamps consentGrantedAt and consentHost to the current base URL`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onModeSelected(CAPABILITY, AiProviderMode.CLOUD)
                awaitItem()
                viewModel.onBaseUrlChanged(CAPABILITY, "https://example.test")
                awaitItem()
                viewModel.onConsentGranted()
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertEquals("https://example.test", row.config.consentHost)
                assertTrue(row.config.consentGrantedAt != null)
            }
        }

    @Test
    fun `changing the base URL to a different host clears prior consent`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onModeSelected(CAPABILITY, AiProviderMode.CLOUD)
                awaitItem()
                viewModel.onBaseUrlChanged(CAPABILITY, "https://example.test")
                awaitItem()
                viewModel.onConsentGranted()
                awaitItem()
                viewModel.onBaseUrlChanged(CAPABILITY, "https://different.test")
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertNull(row.config.consentGrantedAt)
                assertNull(row.config.consentHost)
            }
        }

    @Test
    fun `onApiKeyChanged with a blank key clears it and updates hasApiKey`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onApiKeyChanged(CAPABILITY, "a-real-key")
                val withKey = awaitItem()
                assertTrue(withKey.rows.first { it.capability == CAPABILITY }.hasApiKey)

                viewModel.onApiKeyChanged(CAPABILITY, "")
                val withoutKey = awaitItem()
                assertTrue(!withoutKey.rows.first { it.capability == CAPABILITY }.hasApiKey)
            }
        }

    @Test
    fun `onTestConnection reports the fake repository's real result through the UI state`() =
        runTest {
            // The fake's testConnection doesn't truly suspend, so the Loading
            // tick set just before it and the Done tick set just after it can
            // land in the same StateFlow conflation window with no
            // opportunity for the collector to observe Loading as a distinct
            // item — asserting only the final, real result avoids depending
            // on scheduling that production code doesn't actually guarantee.
            repository.nextTestConnectionResult = AiConnectionTestResult.Failure("no network")
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onTestConnection(CAPABILITY)
                val done = awaitItem()
                val testState = done.rows.first { it.capability == CAPABILITY }.testState as AiConnectionTestState.Done
                assertEquals(AiConnectionTestResult.Failure("no network"), testState.result)
                assertEquals(1, repository.testConnectionCallCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onVendorSelected persists the chosen vendor`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onVendorSelected(CAPABILITY, AiVendor.OPENAI)
                val state = awaitItem()
                assertEquals(
                    AiVendor.OPENAI,
                    state.rows
                        .first { it.capability == CAPABILITY }
                        .config.vendor,
                )
            }
        }

    /** M24 real-device finding: on a physical tablet, granting consent with
     * an empty Base URL left cloud silently unreachable — nothing pre-filled
     * it and nothing told the user why. A vendor with one real, fixed
     * public API root now pre-fills it automatically. */
    @Test
    fun `onVendorSelected pre-fills the vendor's default base URL when the field is empty`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onVendorSelected(CAPABILITY, AiVendor.GEMINI)
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertEquals(AiVendor.GEMINI, row.config.vendor)
                assertEquals("https://generativelanguage.googleapis.com", row.config.baseUrl)
            }
        }

    @Test
    fun `onVendorSelected never overwrites a base URL the user already typed`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onBaseUrlChanged(CAPABILITY, "https://my-proxy.internal")
                awaitItem()
                viewModel.onVendorSelected(CAPABILITY, AiVendor.OPENAI)
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertEquals("https://my-proxy.internal", row.config.baseUrl)
            }
        }

    @Test
    fun `onVendorSelected leaves the base URL blank for vendors with no safe default`() =
        runTest {
            val viewModel = AiProvidersViewModel(repository)
            viewModel.uiState.test {
                awaitRealInitialState()
                viewModel.onVendorSelected(CAPABILITY, AiVendor.OLLAMA)
                val state = awaitItem()
                val row = state.rows.first { it.capability == CAPABILITY }
                assertNull(row.config.baseUrl)
            }
        }
}
