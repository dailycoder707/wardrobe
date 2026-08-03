package com.wardrobe.app.feature.settings.sync

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.sync.ConflictReason
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.PairedDevice
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncEntityType
import com.wardrobe.app.core.model.sync.SyncState
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WardrobeSyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows Connect Phone when no device is paired yet`() {
        composeRule.setContent {
            WardrobeTheme {
                StatusCard(status = SyncStatusSnapshot(), onConnectPhone = {}, onManualSync = {})
            }
        }

        composeRule.onNodeWithText("No device paired yet").assertExists()
        composeRule.onNodeWithText("Connect Phone").assertExists()
    }

    @Test
    fun `shows the paired device name and a Manual Sync action once paired`() {
        val paired =
            PairedDevice(
                deviceId = "phone-1",
                displayName = "Alex's Phone",
                publicKeyFingerprint = "abc123",
                pairedAt = Instant.EPOCH,
                lastSyncAt = Instant.EPOCH,
            )
        composeRule.setContent {
            WardrobeTheme {
                StatusCard(
                    status = SyncStatusSnapshot(state = SyncState.IDLE, connectedDevice = paired),
                    onConnectPhone = {},
                    onManualSync = {},
                )
            }
        }

        composeRule.onNodeWithText("Alex's Phone").assertExists()
        composeRule.onNodeWithText("Manual Sync").assertExists()
    }

    @Test
    fun `tapping Keep this device's version resolves the conflict with KEEP_LOCAL`() {
        var resolution: ConflictResolution? = null
        val conflict =
            SyncConflict(
                id = 1L,
                entityType = SyncEntityType.GARMENT,
                entitySyncId = "garment-1",
                reason = ConflictReason.EDIT_DELETE_CONFLICT,
                localSummary = "Edited on this device: renamed to \"Navy Blazer\"",
                remoteSummary = "Deleted on the other device",
                detectedAt = Instant.EPOCH,
            )

        composeRule.setContent {
            WardrobeTheme {
                ConflictCard(conflict = conflict, onResolve = { resolution = it })
            }
        }

        composeRule.onNodeWithText("Keep this device's version").performClick()

        assert(resolution == ConflictResolution.KEEP_LOCAL)
    }
}
