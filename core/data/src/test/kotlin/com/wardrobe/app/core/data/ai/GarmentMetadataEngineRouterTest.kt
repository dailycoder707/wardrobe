package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.metadata.OnDeviceMetadataEngine
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.GARMENT_METADATA,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.GENERIC_REST,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )

class GarmentMetadataEngineRouterTest {
    @Test
    fun `generateMetadata uses the on-device engine when not cloud-ready`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            val onDeviceResult =
                listOf(MetadataSuggestion(MetadataField.PRIMARY_COLOR, "blue", 0.7f, onDeviceProvenance()))
            coEvery { onDeviceEngine.generateMetadata(any()) } returns onDeviceResult
            val preferences = fakePreferences(AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_METADATA))
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    mockk(),
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            val result = router.generateMetadata(mockk())

            assertSame(onDeviceResult, result)
        }

    /** M24 Phase 7/11#36 — a `CLOUD` mode config with everything else
     * present but no granted consent (`consentGrantedAt = null`) must never
     * dispatch to the cloud gateway at all — [AiProviderConfig.isCloudReady]
     * is the single gate, verified here at the router's own call boundary
     * rather than trusting that function's own unit tests alone. */
    @Test
    fun `generateMetadata never dispatches to the cloud gateway without granted consent`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            coEvery { onDeviceEngine.generateMetadata(any()) } returns emptyList()
            val gateway = mockk<AiGateway>()
            val unconsentedConfig = CLOUD_READY_CONFIG.copy(consentGrantedAt = null, consentHost = null)
            val preferences = fakePreferences(unconsentedConfig)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            router.generateMetadata(mockk())

            coVerify(exactly = 0) { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) }
        }

    /** M24 Phase 7/11#35 — even with a consented, fully-configured `CLOUD`
     * mode, a missing API key must block dispatch just as firmly as missing
     * consent — never silently proceed with a blank/absent credential. */
    @Test
    fun `generateMetadata never dispatches to the cloud gateway without an API key`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            coEvery { onDeviceEngine.generateMetadata(any()) } returns emptyList()
            val gateway = mockk<AiGateway>()
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            router.generateMetadata(mockk())

            coVerify(exactly = 0) { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `generateMetadata parses the cloud response when fully configured`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            val gateway = mockk<AiGateway>()
            val rawResponse =
                """{"suggestions":[{"field":"PRIMARY_COLOR","value":"navy","confidence":0.9}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(rawResponse, cloudProvenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            val result = router.generateMetadata(mockk())

            assertEquals(1, result.size)
            assertEquals(MetadataField.PRIMARY_COLOR, result.first().field)
            assertEquals("navy", result.first().value)
            coVerify(exactly = 0) { onDeviceEngine.generateMetadata(any()) }
        }

    @Test
    fun `generateMetadata dispatches the cloud call with the current METADATA_V3 prompt version`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success("""{"suggestions":[]}""", cloudProvenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            router.generateMetadata(mockk())

            coVerify { gateway.runVisionPrompt(any(), PromptVersions.METADATA_V3, any(), any(), any(), any()) }
        }

    @Test
    fun `generateMetadata requests provider-native structured JSON output, not prompt compliance alone`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success("""{"suggestions":[]}""", cloudProvenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            router.generateMetadata(mockk())

            coVerify { gateway.runVisionPrompt(any(), any(), any(), any(), any(), expectJsonResponse = true) }
        }

    /**
     * A failed cloud dispatch still returns the on-device suggestions —
     * cloud degrades a capability, it never breaks one — but the *values*
     * must be exactly what the on-device engine produced while the
     * provenance records that this was a fallback. Before this, the two were
     * indistinguishable above the router, which is what let the review
     * screen report a failed cloud run as a plain "Provider: On-Device".
     */
    @Test
    fun `a failed cloud dispatch returns on-device values tagged as a cloud fallback`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            coEvery { onDeviceEngine.generateMetadata(any()) } returns
                listOf(MetadataSuggestion(MetadataField.PRIMARY_COLOR, "blue", 0.7f, onDeviceProvenance()))
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Failure("model_not_found: models/x is no longer available")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            val result = router.generateMetadata(mockk())

            val only = result.single()
            // The suggestion itself is untouched — nothing is reinterpreted.
            assertEquals(MetadataField.PRIMARY_COLOR, only.field)
            assertEquals("blue", only.value)
            assertEquals(0.7f, only.confidence)
            // …but it is no longer indistinguishable from a plain local run.
            assertEquals(AiResultSource.ON_DEVICE, only.provenance.source)
            assertEquals(AiResultSource.CLOUD, only.provenance.requestedSource)
            assertTrue(only.provenance.fallbackUsed)
            assertEquals("model_not_found: models/x is no longer available", only.provenance.fallbackReason)
        }

    /** The inverse guarantee: a run the user deliberately configured as
     * on-device must never claim a fallback it did not make. */
    @Test
    fun `a deliberate on-device run reports no fallback`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceMetadataEngine>()
            coEvery { onDeviceEngine.generateMetadata(any()) } returns
                listOf(MetadataSuggestion(MetadataField.PRIMARY_COLOR, "blue", 0.7f, onDeviceProvenance()))
            val preferences = fakePreferences(AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_METADATA))
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router =
                GarmentMetadataEngineRouter(
                    onDeviceEngine,
                    mockk(),
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            val provenance = router.generateMetadata(mockk()).single().provenance

            assertFalse(provenance.fallbackUsed)
            assertEquals(AiResultSource.ON_DEVICE, provenance.requestedSource)
            assertNull(provenance.fallbackReason)
        }

    @Test
    fun `a successful cloud dispatch reports no fallback`() =
        runTest {
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(
                    """{"suggestions":[{"field":"PRIMARY_COLOR","value":"navy","confidence":0.9}]}""",
                    cloudProvenance(),
                )
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentMetadataEngineRouter(
                    mockk(),
                    gateway,
                    preferences,
                    apiKeyStore,
                    fakeReferenceRepositories(),
                    mockk(relaxed = true),
                )

            val provenance = router.generateMetadata(mockk()).single().provenance

            assertEquals(AiResultSource.CLOUD, provenance.source)
            assertFalse(provenance.fallbackUsed)
            assertNull(provenance.fallbackReason)
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }

private fun fakeReferenceRepositories(): MetadataReferenceRepositories =
    MetadataReferenceRepositories(
        categoryRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
        colorRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
        materialRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
        fabricRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
        occasionRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
        tagRepository = mockk { every { observeAll() } returns flowOf(emptyList()) },
    )

private fun onDeviceProvenance(): AiResultProvenance =
    AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH)

private fun cloudProvenance(): AiResultProvenance =
    AiResultProvenance(AiResultSource.CLOUD, "generic", null, "metadata-v1", Instant.EPOCH)
