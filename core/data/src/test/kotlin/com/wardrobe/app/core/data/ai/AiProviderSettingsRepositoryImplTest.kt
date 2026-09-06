package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.database.dao.AiCallLogDao
import com.wardrobe.app.core.database.dao.AiJobDao
import com.wardrobe.app.core.database.entity.AiCallLogEntity
import com.wardrobe.app.core.database.entity.AiJobEntity
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiJobStatus
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.GARMENT_METADATA,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.OPENAI,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )

private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

@RunWith(RobolectricTestRunner::class)
class AiProviderSettingsRepositoryImplTest {
    @Test
    fun `testConnection reports failure without dispatching when not cloud-ready`() =
        runTest {
            val preferences = fakePreferences(AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_METADATA))
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val gateway = mockk<AiGateway>()
            val repository =
                AiProviderSettingsRepositoryImpl(preferences, apiKeyStore, gateway, mockk(), mockk(), FIXED_CLOCK)

            val result = repository.testConnection(AiCapability.GARMENT_METADATA)

            assertTrue(result is AiConnectionTestResult.Failure)
        }

    @Test
    fun `testConnection dispatches a vision prompt for a non-generic vendor and reports success`() =
        runTest {
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns visionSuccess()
            val repository =
                AiProviderSettingsRepositoryImpl(preferences, apiKeyStore, gateway, mockk(), mockk(), FIXED_CLOCK)

            val result = repository.testConnection(AiCapability.GARMENT_METADATA)

            assertTrue(result is AiConnectionTestResult.Success)
        }

    @Test
    fun `testConnection dispatches an image task for the generic REST vendor`() =
        runTest {
            val config = CLOUD_READY_CONFIG.copy(vendor = AiVendor.GENERIC_REST)
            val preferences = fakePreferences(config)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns imageTaskSuccess()
            val repository =
                AiProviderSettingsRepositoryImpl(preferences, apiKeyStore, gateway, mockk(), mockk(), FIXED_CLOCK)

            val result = repository.testConnection(AiCapability.GARMENT_METADATA)

            assertTrue(result is AiConnectionTestResult.Success)
        }

    @Test
    fun `testConnection reports the failure reason when the gateway dispatch fails`() =
        runTest {
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Failure("timeout")
            val repository =
                AiProviderSettingsRepositoryImpl(preferences, apiKeyStore, gateway, mockk(), mockk(), FIXED_CLOCK)

            val result = repository.testConnection(AiCapability.GARMENT_METADATA) as AiConnectionTestResult.Failure

            assertEquals("timeout", result.reason)
        }

    @Test
    fun `hasApiKey reflects whether a non-blank key is stored`() =
        runTest {
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    apiKeyStore,
                    mockk(),
                    mockk(),
                    mockk(),
                    FIXED_CLOCK,
                )

            assertEquals(true, repository.hasApiKey(AiCapability.GARMENT_METADATA))
        }

    @Test
    fun `hasApiKey is false when no key or only a blank one is stored`() =
        runTest {
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "   " }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    apiKeyStore,
                    mockk(),
                    mockk(),
                    mockk(),
                    FIXED_CLOCK,
                )

            assertEquals(false, repository.hasApiKey(AiCapability.GARMENT_METADATA))
        }

    @Test
    fun `observeRecentActivity projects real ai_call_log rows and respects the limit`() =
        runTest {
            val rows =
                listOf(
                    logEntity(AiCapability.GARMENT_METADATA, timestamp = 3000L),
                    logEntity(AiCapability.OUTFIT_STYLING, timestamp = 2000L),
                    logEntity(AiCapability.VIRTUAL_TRY_ON, timestamp = 1000L),
                )
            val aiCallLogDao = mockk<AiCallLogDao> { every { observeAll() } returns flowOf(rows) }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    mockk(),
                    mockk(),
                    aiCallLogDao,
                    mockk(),
                    FIXED_CLOCK,
                )

            val activity = repository.observeRecentActivity(limit = 2).first()

            assertEquals(2, activity.size)
            assertEquals(AiCapability.GARMENT_METADATA, activity[0].capability)
            assertEquals(AiCapability.OUTFIT_STYLING, activity[1].capability)
        }

    @Test
    fun `observeRecentActivity is empty when ai_call_log has no rows, never a fabricated entry`() =
        runTest {
            val aiCallLogDao = mockk<AiCallLogDao> { every { observeAll() } returns flowOf(emptyList()) }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    mockk(),
                    mockk(),
                    aiCallLogDao,
                    mockk(),
                    FIXED_CLOCK,
                )

            val activity = repository.observeRecentActivity().first()

            assertTrue(activity.isEmpty())
        }

    @Test
    fun `observeActiveOperations keeps only genuinely not-yet-terminal job rows`() =
        runTest {
            val rows =
                listOf(
                    jobEntity(AiCapability.GARMENT_METADATA, AiJobStatus.RUNNING, createdAt = 3000L),
                    jobEntity(AiCapability.OUTFIT_STYLING, AiJobStatus.PENDING, createdAt = 2000L),
                    jobEntity(AiCapability.GARMENT_EXTRACTION, AiJobStatus.SUCCEEDED, createdAt = 1000L),
                    jobEntity(AiCapability.VIRTUAL_TRY_ON, AiJobStatus.FAILED, createdAt = 500L),
                    jobEntity(AiCapability.GARMENT_RECONSTRUCTION, AiJobStatus.CANCELLED, createdAt = 100L),
                )
            val aiJobDao = mockk<AiJobDao> { every { observeAll() } returns flowOf(rows) }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    mockk(),
                    mockk(),
                    mockk(),
                    aiJobDao,
                    FIXED_CLOCK,
                )

            val active = repository.observeActiveOperations().first()

            assertEquals(2, active.size)
            assertEquals(AiCapability.GARMENT_METADATA, active[0].capability)
            assertEquals(AiJobStatus.RUNNING, active[0].status)
            assertEquals(AiCapability.OUTFIT_STYLING, active[1].capability)
        }

    @Test
    fun `observeActiveOperations is empty when nothing is genuinely in flight, never fabricated`() =
        runTest {
            val rows = listOf(jobEntity(AiCapability.GARMENT_METADATA, AiJobStatus.SUCCEEDED, createdAt = 1000L))
            val aiJobDao = mockk<AiJobDao> { every { observeAll() } returns flowOf(rows) }
            val repository =
                AiProviderSettingsRepositoryImpl(
                    fakePreferences(CLOUD_READY_CONFIG),
                    mockk(),
                    mockk(),
                    mockk(),
                    aiJobDao,
                    FIXED_CLOCK,
                )

            assertTrue(repository.observeActiveOperations().first().isEmpty())
        }
}

private fun jobEntity(
    capability: AiCapability,
    status: AiJobStatus,
    createdAt: Long,
) = AiJobEntity(
    capability = capability,
    cacheKey = "key-$capability-$createdAt",
    status = status,
    errorMessage = null,
    createdAt = createdAt,
    updatedAt = createdAt,
)

private fun logEntity(
    capability: AiCapability,
    timestamp: Long,
) = AiCallLogEntity(
    capability = capability,
    provider = null,
    model = null,
    latencyMs = 10L,
    outcome = AiCallOutcome.SUCCESS,
    confidence = null,
    estimatedInputTokens = null,
    estimatedOutputTokens = null,
    estimatedCostMinorUnits = null,
    cacheHit = false,
    timestamp = timestamp,
)

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }

private fun visionSuccess(): VisionPromptResult.Success =
    VisionPromptResult.Success(
        "OK",
        AiResultProvenance(AiResultSource.CLOUD, "openai", null, "test-connection-v1", Instant.EPOCH),
    )

private fun imageTaskSuccess(): ImageTaskResult.Success =
    ImageTaskResult.Success(
        mockk(),
        confidence = null,
        provenance = AiResultProvenance(AiResultSource.CLOUD, "generic", null, "test-connection-v1", Instant.EPOCH),
    )
