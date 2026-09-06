package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AiProviderPreferencesDataStoreTest {
    private fun newDataStore(fileName: String): androidx.datastore.core.DataStore<Preferences> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(fileName) },
        )
    }

    @Test
    fun `observeConfig defaults to on-device with no vendor when nothing has been set`() =
        runTest {
            val store = AiProviderPreferencesDataStore(newDataStore("ai-prefs-default"))

            val config = store.observeConfig(AiCapability.GARMENT_METADATA).first()

            assertEquals(AiProviderMode.ON_DEVICE, config.mode)
            assertNull(config.vendor)
            assertNull(config.baseUrl)
            assertNull(config.consentGrantedAt)
        }

    @Test
    fun `setConfig round-trips every field for a cloud-configured capability`() =
        runTest {
            val store = AiProviderPreferencesDataStore(newDataStore("ai-prefs-roundtrip"))
            val consentTime = Instant.ofEpochMilli(1_700_000_000_000L)
            val config =
                AiProviderConfig(
                    capability = AiCapability.GARMENT_EXTRACTION,
                    mode = AiProviderMode.CLOUD,
                    vendor = AiVendor.OPENAI,
                    baseUrl = "https://api.openai.com",
                    model = "gpt-vision",
                    costRatePerThousandTokens = 0.01,
                    consentGrantedAt = consentTime,
                    consentHost = "https://api.openai.com",
                )

            store.setConfig(config)
            val reloaded = store.observeConfig(AiCapability.GARMENT_EXTRACTION).first()

            assertEquals(config, reloaded)
            assertTrue(reloaded.isCloudReady())
        }

    @Test
    fun `setConfig for one capability never affects another capability's stored config`() =
        runTest {
            val store = AiProviderPreferencesDataStore(newDataStore("ai-prefs-isolation"))
            store.setConfig(
                AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_METADATA).copy(
                    mode = AiProviderMode.CLOUD,
                    vendor = AiVendor.GEMINI,
                    baseUrl = "https://example.test",
                ),
            )

            val other = store.observeConfig(AiCapability.OUTFIT_STYLING).first()

            assertEquals(AiProviderMode.ON_DEVICE, other.mode)
            assertNull(other.vendor)
        }

    @Test
    fun `clearing a field back to null actually removes it, not just blanks it`() =
        runTest {
            val store = AiProviderPreferencesDataStore(newDataStore("ai-prefs-clear"))
            val capability = AiCapability.VIRTUAL_TRY_ON
            store.setConfig(
                AiProviderConfig
                    .onDeviceDefault(
                        capability,
                    ).copy(mode = AiProviderMode.CLOUD, baseUrl = "https://a.test"),
            )

            store.setConfig(AiProviderConfig.onDeviceDefault(capability))
            val reloaded = store.observeConfig(capability).first()

            assertEquals(AiProviderMode.ON_DEVICE, reloaded.mode)
            assertNull(reloaded.baseUrl)
        }
}
