package com.wardrobe.app.core.sync.transport

import com.wardrobe.app.core.sync.crypto.DeviceIdentityKeyStore
import com.wardrobe.app.core.sync.crypto.SessionCrypto
import com.wardrobe.app.core.sync.crypto.verifySignature
import com.wardrobe.app.core.sync.protocol.HandshakeInitBody
import com.wardrobe.app.core.sync.protocol.HandshakeResponseBody
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/** Thrown when the peer either isn't a device this one has paired with, or
 * presents a signature that doesn't match the identity key pinned at
 * pairing time — "prevent unknown devices joining" made concrete. */
class UnknownPeerException(
    message: String,
) : Exception(message)

/**
 * The pre-data handshake (Phase 8) — necessarily plaintext-framed (there is
 * no session key yet to encrypt with), but never *unauthenticated*: each
 * side signs its own fresh ephemeral public key with the long-term identity
 * key the other side already pinned during QR pairing, so a device on the
 * network that was never paired cannot complete this exchange no matter
 * what it sends. Once both signatures check out,
 * [SessionCrypto.deriveSessionKey] gives both sides the same AES key
 * without either ever having sent it.
 */
class SyncHandshake(
    private val identityKeyStore: DeviceIdentityKeyStore,
    private val localDeviceId: String,
) {
    /** Called by whichever side opened the socket connection. */
    fun performAsInitiator(
        inputStream: InputStream,
        outputStream: OutputStream,
        resolvePeerPublicKey: (deviceId: String) -> PublicKey?,
    ): SecretKeySpec {
        val ephemeralKeyPair = SessionCrypto.generateEphemeralKeyPair()
        val ephemeralPublicBytes = ephemeralKeyPair.public.encoded
        PlainJsonFrameIo.write(
            outputStream,
            HandshakeInitBody.serializer(),
            HandshakeInitBody(
                deviceId = localDeviceId,
                ephemeralPublicKeyBase64 = encode(ephemeralPublicBytes),
                signatureBase64 = encode(identityKeyStore.sign(ephemeralPublicBytes)),
            ),
        )

        val response = PlainJsonFrameIo.read(inputStream, HandshakeResponseBody.serializer())
        val peerIdentityKey = requirePeerKey(resolvePeerPublicKey, response.deviceId)
        val peerEphemeralBytes = decode(response.ephemeralPublicKeyBase64)
        requireValidSignature(peerIdentityKey, peerEphemeralBytes, decode(response.signatureBase64), response.deviceId)
        return SessionCrypto.deriveSessionKey(ephemeralKeyPair, SessionCrypto.decodePublicKey(peerEphemeralBytes))
    }

    /** Called by whichever side accepted the incoming socket connection. */
    fun performAsResponder(
        inputStream: InputStream,
        outputStream: OutputStream,
        resolvePeerPublicKey: (deviceId: String) -> PublicKey?,
    ): SecretKeySpec {
        val init = PlainJsonFrameIo.read(inputStream, HandshakeInitBody.serializer())
        val peerIdentityKey = requirePeerKey(resolvePeerPublicKey, init.deviceId)
        val peerEphemeralBytes = decode(init.ephemeralPublicKeyBase64)
        requireValidSignature(peerIdentityKey, peerEphemeralBytes, decode(init.signatureBase64), init.deviceId)

        val ephemeralKeyPair = SessionCrypto.generateEphemeralKeyPair()
        val ephemeralPublicBytes = ephemeralKeyPair.public.encoded
        PlainJsonFrameIo.write(
            outputStream,
            HandshakeResponseBody.serializer(),
            HandshakeResponseBody(
                deviceId = localDeviceId,
                ephemeralPublicKeyBase64 = encode(ephemeralPublicBytes),
                signatureBase64 = encode(identityKeyStore.sign(ephemeralPublicBytes)),
            ),
        )
        return SessionCrypto.deriveSessionKey(ephemeralKeyPair, SessionCrypto.decodePublicKey(peerEphemeralBytes))
    }

    private fun requirePeerKey(
        resolvePeerPublicKey: (deviceId: String) -> PublicKey?,
        deviceId: String,
    ): PublicKey = resolvePeerPublicKey(deviceId) ?: throw UnknownPeerException("No paired device with id $deviceId")

    private fun requireValidSignature(
        peerIdentityKey: PublicKey,
        signedData: ByteArray,
        signature: ByteArray,
        deviceId: String,
    ) {
        if (!verifySignature(peerIdentityKey, signedData, signature)) {
            throw UnknownPeerException("Signature from $deviceId did not match its pinned identity key")
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
