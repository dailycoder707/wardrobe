package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A device this one has paired with (Phase 8) — this app's pairing model is
 * deliberately one-tablet-plus-one-phone simple, but the schema doesn't
 * hardcode a count; nothing stops a third device pairing later. [deviceId]
 * is the peer's own self-generated identifier, not this device's.
 * [publicKeyBase64] is the peer's long-term public key, pinned at pairing
 * time (TOFU — trust-on-first-use, since the pairing QR exchange is the
 * out-of-band channel that establishes trust); every later session must
 * present a key that hashes to [publicKeyFingerprint] or the connection is
 * refused. [lastSyncedChangeLogId] is this device's own sync cursor *for
 * this peer* — the highest local `sync_change_log.id` already sent to it.
 */
@Entity(tableName = "paired_device")
data class PairedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val publicKeyFingerprint: String,
    val publicKeyBase64: String,
    val pairedAt: Long,
    val lastSyncAt: Long?,
    val lastSyncedChangeLogId: Long = 0,
)
