package com.wardrobe.app.core.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class SessionCryptoTest {
    @Test
    fun `both sides of an ECDH exchange derive the identical session key`() {
        val deviceA = SessionCrypto.generateEphemeralKeyPair()
        val deviceB = SessionCrypto.generateEphemeralKeyPair()

        val keyFromA = SessionCrypto.deriveSessionKey(deviceA, deviceB.public)
        val keyFromB = SessionCrypto.deriveSessionKey(deviceB, deviceA.public)

        assertArrayEquals(keyFromA.encoded, keyFromB.encoded)
    }

    @Test
    fun `two different sessions derive different keys`() {
        val deviceA1 = SessionCrypto.generateEphemeralKeyPair()
        val deviceB1 = SessionCrypto.generateEphemeralKeyPair()
        val sessionOneKey = SessionCrypto.deriveSessionKey(deviceA1, deviceB1.public)

        val deviceA2 = SessionCrypto.generateEphemeralKeyPair()
        val deviceB2 = SessionCrypto.generateEphemeralKeyPair()
        val sessionTwoKey = SessionCrypto.deriveSessionKey(deviceA2, deviceB2.public)

        assertNotEquals(sessionOneKey.encoded.toList(), sessionTwoKey.encoded.toList())
    }

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val key =
            SessionCrypto.deriveSessionKey(
                SessionCrypto.generateEphemeralKeyPair(),
                SessionCrypto.generateEphemeralKeyPair().public,
            )
        val iv = SessionCrypto.randomIv()
        val plaintext = "a garment changed on this device".toByteArray()
        val aad = "0".toByteArray()

        val ciphertext = SessionCrypto.encrypt(key, iv, plaintext, aad)
        val decrypted = SessionCrypto.decrypt(key, iv, ciphertext, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypting with a mismatched sequence number as AAD fails authentication`() {
        val key =
            SessionCrypto.deriveSessionKey(
                SessionCrypto.generateEphemeralKeyPair(),
                SessionCrypto.generateEphemeralKeyPair().public,
            )
        val iv = SessionCrypto.randomIv()
        val ciphertext = SessionCrypto.encrypt(key, iv, "payload".toByteArray(), "0".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            SessionCrypto.decrypt(key, iv, ciphertext, "1".toByteArray())
        }
    }

    @Test
    fun `a tampered ciphertext byte fails authentication rather than decrypting silently`() {
        val key =
            SessionCrypto.deriveSessionKey(
                SessionCrypto.generateEphemeralKeyPair(),
                SessionCrypto.generateEphemeralKeyPair().public,
            )
        val iv = SessionCrypto.randomIv()
        val ciphertext = SessionCrypto.encrypt(key, iv, "payload".toByteArray(), "0".toByteArray())
        ciphertext[0] = ciphertext[0].inc()

        assertThrows(AEADBadTagException::class.java) {
            SessionCrypto.decrypt(key, iv, ciphertext, "0".toByteArray())
        }
    }

    @Test
    fun `decodePublicKey reverses the encoded form of a generated key`() {
        val keyPair = SessionCrypto.generateEphemeralKeyPair()
        val decoded = SessionCrypto.decodePublicKey(keyPair.public.encoded)
        assertEquals(keyPair.public, decoded)
    }
}
