package com.wardrobe.app.core.sync.pairing

import kotlinx.serialization.Serializable

/**
 * Everything the QR code encodes (Phase 8). This — not a server, not an
 * account — is the entire trust anchor: whoever physically scans this QR is
 * trusted as the pairing peer (Constitution: no accounts, no email, no
 * passwords, no internet). [pairingToken] is single-use and short-lived
 * (checked by the host, not encoded as an expiry here, since the two
 * devices' clocks aren't assumed to be in sync) — it exists so a photograph
 * of an old QR code can't be replayed to re-trigger pairing later.
 */
@Serializable
data class PairingOfferPayload(
    val deviceId: String,
    val displayName: String,
    val identityPublicKeyBase64: String,
    val pairingToken: String,
    val hostAddress: String,
    val hostPort: Int,
)
