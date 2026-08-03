package com.wardrobe.app.core.sync.pairing

import kotlinx.serialization.Serializable

/** Sent by the scanning device immediately after connecting to the host —
 * [token] must match the [PairingOfferPayload.pairingToken] encoded in the
 * QR the host is displaying, which is the only proof the host needs that
 * whoever connected actually scanned the real, currently-displayed code
 * (not a stale photo of an old one, not an unrelated device probing the
 * port). */
@Serializable
data class PairingConfirmBody(
    val deviceId: String,
    val displayName: String,
    val identityPublicKeyBase64: String,
    val token: String,
)

/** The host's reply — its own identity, so the scanning device can pin the
 * host's key too (pairing is mutual: both ends end up with a
 * [com.wardrobe.app.core.model.sync.PairedDevice] row for the other). */
@Serializable
data class PairingAcceptBody(
    val deviceId: String,
    val displayName: String,
    val identityPublicKeyBase64: String,
)
