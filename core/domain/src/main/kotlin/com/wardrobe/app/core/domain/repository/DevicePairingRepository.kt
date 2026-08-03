package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.sync.PairedDevice
import kotlinx.coroutines.flow.Flow

/**
 * A freshly generated pairing offer, pre-rendered as PNG-encoded QR image
 * bytes — `core:domain` has zero Android dependency (not even
 * `android.graphics.Bitmap`), so the repository does the QR rendering
 * itself and hands back plain bytes a Compose `Image` can decode directly
 * (`BitmapFactory.decodeByteArray`), rather than exposing any `core:sync`
 * wire type across the domain boundary. Constitution: no accounts, no
 * email, no passwords, no internet — the QR code itself, physically
 * scanned, is the entire trust exchange.
 */
typealias PairingOfferImage = ByteArray

/**
 * Device pairing (Phase 8) — generating/consuming the QR pairing exchange
 * and the resulting registry of paired devices. See
 * `phase-8-multi-device-sync.md`'s "Pairing" and "Security" sections for the
 * full handshake and why a QR code (not a PIN, not an account) is the trust
 * anchor.
 */
interface DevicePairingRepository {
    /** Called on the device showing the QR code ("tablet" role in the
     * brief, though the role is symmetric — either device can host). Starts
     * listening for the one incoming pairing connection the QR describes;
     * call [cancelPairingOffer] if the user navigates away before a phone
     * scans it. */
    suspend fun generatePairingOfferImage(): PairingOfferImage

    suspend fun cancelPairingOffer()

    /**
     * Called on the device that scanned the QR code, with the raw decoded
     * QR text (`feature:settings` decodes the camera frame itself via
     * `core:sync`'s `PairingQrCodec` — a narrow, documented exception to
     * "features depend only on `core:domain`," the same precedent the
     * Developer Panel already set for `core:image`; see
     * `phase-8-multi-device-sync.md`'s "Architecture" section). Completes
     * the key exchange and persists the new [PairedDevice] on success.
     */
    suspend fun completePairing(scannedQrText: String): Result<PairedDevice>

    fun observePairedDevices(): Flow<List<PairedDevice>>

    suspend fun unpairDevice(deviceId: String)
}
