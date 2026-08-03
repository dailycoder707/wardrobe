package com.wardrobe.app.core.model.sync

import java.time.Instant

/**
 * A device this one has completed the pairing handshake with (Phase 8).
 * [publicKeyFingerprint] is the SHA-256 of the peer's long-term public key —
 * displayed in Settings so a user can visually confirm which physical device
 * an entry refers to, and used to detect a peer presenting a different key
 * than the one pinned at pairing time (see `phase-8-multi-device-sync.md`'s
 * "Security" section).
 */
data class PairedDevice(
    val deviceId: String,
    val displayName: String,
    val publicKeyFingerprint: String,
    val pairedAt: Instant,
    val lastSyncAt: Instant?,
)
