package com.wardrobe.app.core.sync.transport

import com.wardrobe.app.core.sync.crypto.DeviceIdentityKeyStore
import com.wardrobe.app.core.sync.crypto.verifySignature
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature

/** A plain, software-generated identity keypair — [SyncHandshakeTest] never
 * touches `AndroidKeyStore`, so this is a real substitute, not a mock of
 * behavior: the real [com.wardrobe.app.core.sync.crypto.AndroidKeystoreDeviceIdentity]
 * only differs in *where* the private key lives, never in the sign/verify
 * math this test exercises. */
private class FakeDeviceIdentity : DeviceIdentityKeyStore {
    private val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()

    override fun getOrCreatePublicKey(): PublicKey = keyPair.public

    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(data)
            sign()
        }
}

private class PipedDuplex {
    val initiatorIn = PipedInputStream()
    val responderOut = PipedOutputStream(initiatorIn)
    val responderIn = PipedInputStream()
    val initiatorOut = PipedOutputStream(responderIn)
}

class SyncHandshakeTest {
    @Test
    fun `initiator and responder derive the same session key when both identities are pinned`() {
        val duplex = PipedDuplex()
        val initiatorIdentity = FakeDeviceIdentity()
        val responderIdentity = FakeDeviceIdentity()
        val initiatorHandshake = SyncHandshake(initiatorIdentity, "tablet")
        val responderHandshake = SyncHandshake(responderIdentity, "phone")

        var initiatorKey: ByteArray? = null
        var responderKey: ByteArray? = null

        val responderThread =
            Thread {
                responderKey =
                    responderHandshake
                        .performAsResponder(duplex.responderIn, duplex.responderOut) { deviceId ->
                            if (deviceId == "tablet") initiatorIdentity.getOrCreatePublicKey() else null
                        }.encoded
            }
        val initiatorThread =
            Thread {
                initiatorKey =
                    initiatorHandshake
                        .performAsInitiator(duplex.initiatorIn, duplex.initiatorOut) { deviceId ->
                            if (deviceId == "phone") responderIdentity.getOrCreatePublicKey() else null
                        }.encoded
            }

        responderThread.start()
        initiatorThread.start()
        responderThread.join()
        initiatorThread.join()

        assertArrayEquals(initiatorKey, responderKey)
    }

    @Test
    fun `an initiator rejects a responder whose device id was never pinned`() {
        val duplex = PipedDuplex()
        val initiatorIdentity = FakeDeviceIdentity()
        val responderIdentity = FakeDeviceIdentity()
        val initiatorHandshake = SyncHandshake(initiatorIdentity, "tablet")
        val responderHandshake = SyncHandshake(responderIdentity, "phone")

        val responderThread =
            Thread {
                runCatching {
                    responderHandshake.performAsResponder(duplex.responderIn, duplex.responderOut) { deviceId ->
                        if (deviceId == "tablet") initiatorIdentity.getOrCreatePublicKey() else null
                    }
                }
            }
        responderThread.start()

        assertThrows(UnknownPeerException::class.java) {
            initiatorHandshake.performAsInitiator(duplex.initiatorIn, duplex.initiatorOut) { null }
        }
        responderThread.join()
    }

    @Test
    fun `verifySignature rejects a signature made by a different key`() {
        val signer = FakeDeviceIdentity()
        val impostor = FakeDeviceIdentity()
        val data = "ephemeral-public-key-bytes".toByteArray()
        val signature = signer.sign(data)

        val validAgainstSigner = verifySignature(signer.getOrCreatePublicKey(), data, signature)
        val validAgainstImpostor = verifySignature(impostor.getOrCreatePublicKey(), data, signature)

        org.junit.Assert.assertTrue(validAgainstSigner)
        org.junit.Assert.assertFalse(validAgainstImpostor)
    }
}
