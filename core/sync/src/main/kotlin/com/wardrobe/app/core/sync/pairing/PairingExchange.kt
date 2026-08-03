package com.wardrobe.app.core.sync.pairing

import com.wardrobe.app.core.sync.transport.PlainJsonFrameIo
import java.io.InputStream
import java.io.OutputStream

/** The peer's identity, learned via pairing — [core:data] turns this into a
 * persisted `PairedDeviceEntity` row. */
data class PairingResult(
    val deviceId: String,
    val displayName: String,
    val identityPublicKeyBase64: String,
)

/** Thrown when the connecting device's token doesn't match the one this
 * host encoded into the QR it's currently displaying. */
class PairingTokenMismatchException : Exception("Pairing token did not match the currently displayed QR code")

/**
 * The one-time exchange that follows a QR scan (Phase 8) — deliberately
 * simpler than [com.wardrobe.app.core.sync.transport.SyncHandshake]: at this
 * point neither device has pinned the other's identity key yet, so there is
 * nothing to sign against. Trust instead comes entirely from [PairingOfferPayload.pairingToken]:
 * only a device that actually scanned the QR currently on screen knows it.
 */
object PairingExchange {
    /** Runs on the device that generated and is displaying the QR code. */
    fun acceptIncoming(
        inputStream: InputStream,
        outputStream: OutputStream,
        expectedToken: String,
        localDeviceId: String,
        localDisplayName: String,
        localIdentityPublicKeyBase64: String,
    ): PairingResult {
        val confirm = PlainJsonFrameIo.read(inputStream, PairingConfirmBody.serializer())
        if (confirm.token != expectedToken) throw PairingTokenMismatchException()

        PlainJsonFrameIo.write(
            outputStream,
            PairingAcceptBody.serializer(),
            PairingAcceptBody(localDeviceId, localDisplayName, localIdentityPublicKeyBase64),
        )
        return PairingResult(confirm.deviceId, confirm.displayName, confirm.identityPublicKeyBase64)
    }

    /** Runs on the device that scanned the QR code. */
    fun connectAndConfirm(
        inputStream: InputStream,
        outputStream: OutputStream,
        offer: PairingOfferPayload,
        localDeviceId: String,
        localDisplayName: String,
        localIdentityPublicKeyBase64: String,
    ): PairingResult {
        PlainJsonFrameIo.write(
            outputStream,
            PairingConfirmBody.serializer(),
            PairingConfirmBody(localDeviceId, localDisplayName, localIdentityPublicKeyBase64, offer.pairingToken),
        )
        val accept = PlainJsonFrameIo.read(inputStream, PairingAcceptBody.serializer())
        return PairingResult(accept.deviceId, accept.displayName, accept.identityPublicKeyBase64)
    }
}
