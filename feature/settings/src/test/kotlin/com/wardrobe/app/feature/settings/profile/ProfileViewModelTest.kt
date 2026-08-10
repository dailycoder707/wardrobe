package com.wardrobe.app.feature.settings.profile

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.domain.profile.MAX_DISPLAY_NAME_LENGTH
import com.wardrobe.app.core.image.capture.GalleryImportSource
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.sync.PairedDevice
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import com.wardrobe.app.feature.settings.aiproviders.FakeAiProviderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

private suspend fun ReceiveTurbine<ProfileUiState>.awaitMatching(
    predicate: (ProfileUiState) -> Boolean,
): ProfileUiState {
    var state = awaitItem()
    while (!predicate(state)) {
        state = awaitItem()
    }
    return state
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
    private lateinit var personalization: FakePersonalizationRepository
    private lateinit var aiSettings: FakeAiProviderSettingsRepository
    private lateinit var sync: FakeSyncRepository
    private lateinit var galleryImportSource: GalleryImportSource

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        personalization = FakePersonalizationRepository()
        aiSettings = FakeAiProviderSettingsRepository()
        sync = FakeSyncRepository()
        galleryImportSource = GalleryImportSource(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(appVersion: String = "1.2.3") =
        ProfileViewModel(personalization, aiSettings, sync, galleryImportSource, appVersion)

    @Test
    fun `loading a fresh profile with no name shows the What's your name state`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(null, state.savedName)
                assertEquals("", state.nameDraft)
            }
        }

    @Test
    fun `loading an existing profile shows its saved name as the draft`() =
        runTest {
            personalization = FakePersonalizationRepository(PersonalizationSettings.DEFAULT.copy(displayName = "Palak"))
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("Palak", state.savedName)
                assertEquals("Palak", state.nameDraft)
            }
        }

    @Test
    fun `saving a valid name persists it and clears the draft back to the saved value`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onNameDraftChanged("Alex")
                awaitMatching { it.nameDraft == "Alex" }
                vm.onSaveName()
                val saved = awaitMatching { it.savedName == "Alex" }
                assertEquals("Alex", saved.nameDraft)
                assertEquals(null, saved.nameError)
            }
        }

    @Test
    fun `saving trims surrounding whitespace before persisting`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onNameDraftChanged("  Alex  ")
                awaitMatching { it.nameDraft == "  Alex  " }
                vm.onSaveName()
                val saved = awaitMatching { it.savedName != null }
                assertEquals("Alex", saved.savedName)
            }
        }

    @Test
    fun `saving a blank name is rejected and never touches the persisted name`() =
        runTest {
            personalization =
                FakePersonalizationRepository(PersonalizationSettings.DEFAULT.copy(displayName = "Original"))
            val vm = viewModel()
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onNameDraftChanged("   ")
                awaitMatching { it.nameDraft == "   " }
                vm.onSaveName()
                val afterSave = awaitMatching { it.nameError != null }
                assertEquals("Name can't be empty.", afterSave.nameError)
                assertEquals("Original", afterSave.savedName)
            }
        }

    @Test
    fun `saving a name over the maximum length is rejected and never touches the persisted name`() =
        runTest {
            personalization =
                FakePersonalizationRepository(PersonalizationSettings.DEFAULT.copy(displayName = "Original"))
            val vm = viewModel()
            val tooLong = "A".repeat(MAX_DISPLAY_NAME_LENGTH + 1)
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onNameDraftChanged(tooLong)
                awaitMatching { it.nameDraft == tooLong }
                vm.onSaveName()
                val afterSave = awaitMatching { it.nameError != null }
                assertTrue(afterSave.nameError!!.contains(MAX_DISPLAY_NAME_LENGTH.toString()))
                assertEquals("Original", afterSave.savedName)
            }
        }

    @Test
    fun `a Unicode name saves and reloads exactly as typed`() =
        runTest {
            val vm = viewModel()
            val unicodeName = "Élodie 李雷"
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onNameDraftChanged(unicodeName)
                awaitMatching { it.nameDraft == unicodeName }
                vm.onSaveName()
                val saved = awaitMatching { it.savedName == unicodeName }
                assertEquals(unicodeName, saved.savedName)
            }

            // Persistence across a reload: a second ViewModel reading the same
            // (fake, but shared) repository instance sees the saved name.
            val reloaded = ProfileViewModel(personalization, aiSettings, sync, galleryImportSource, "1.0")
            reloaded.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(unicodeName, state.savedName)
            }
        }

    @Test
    fun `AI configured count reflects how many capabilities are Cloud-ready`() =
        runTest {
            aiSettings.configsFlow.value =
                aiSettings.configsFlow.value +
                (
                    AiCapability.GARMENT_METADATA to
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
                )
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(1, state.cloudConfiguredCapabilityCount)
                assertEquals(AiCapability.entries.size, state.totalCapabilityCount)
            }
        }

    @Test
    fun `sync status with no connected device shows no device connected`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(null, state.connectedDeviceName)
            }
        }

    @Test
    fun `sync status with a connected device surfaces its name`() =
        runTest {
            sync =
                FakeSyncRepository(
                    SyncStatusSnapshot(
                        connectedDevice =
                            PairedDevice(
                                deviceId = "device-1",
                                displayName = "Palak's Tablet",
                                publicKeyFingerprint = "abc123",
                                pairedAt = Instant.EPOCH,
                                lastSyncAt = Instant.EPOCH,
                            ),
                    ),
                )
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("Palak's Tablet", state.connectedDeviceName)
            }
        }

    @Test
    fun `the app version passed to the constructor is exposed unchanged`() =
        runTest {
            val vm = viewModel(appVersion = "9.9.9")
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("9.9.9", state.appVersion)
            }
        }

    @Test
    fun `picking an avatar copies it locally and persists a real file path that survives a reload`() =
        runTest {
            val sourceFile = File.createTempFile("avatar-source", ".jpg")
            sourceFile.writeBytes(byteArrayOf(1, 2, 3))
            val vm = viewModel()
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onAvatarPicked(Uri.fromFile(sourceFile))
                val state = awaitMatching { it.avatarImageUri != null }
                val copied = File(state.avatarImageUri!!)
                assertTrue("the avatar must be copied into this app's own storage", copied.exists())
                assertTrue(copied.absolutePath != sourceFile.absolutePath)
            }

            val reloaded = ProfileViewModel(personalization, aiSettings, sync, galleryImportSource, "1.0")
            reloaded.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.avatarImageUri != null)
            }
        }
}
