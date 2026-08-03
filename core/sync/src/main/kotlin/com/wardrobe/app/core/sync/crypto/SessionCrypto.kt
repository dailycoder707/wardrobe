package com.wardrobe.app.core.sync.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val EC_CURVE_ALGORITHM = "EC"
private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
private const val HMAC_ALGORITHM = "HmacSHA256"
private const val AES_ALGORITHM = "AES"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_BYTES = 32
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12
private const val HKDF_INFO = "wardrobe-sync-session-v1"

/**
 * Everything after identity is authenticated (see [DeviceIdentityKeyStore])
 * is plain, portable JCA — no `AndroidKeyStore` involvement, so this whole
 * class is unit-testable on the JVM with ordinary generated keys, unlike the
 * identity layer above it.
 *
 * Deliberately a *fresh* ephemeral EC keypair per sync session (forward
 * secrecy: recording one session's encrypted traffic doesn't help decrypt
 * any other session, even if the long-term identity key were ever
 * compromised) — see `phase-8-multi-device-sync.md`'s "Encryption" section.
 */
object SessionCrypto {
    fun generateEphemeralKeyPair(): KeyPair = KeyPairGenerator.getInstance(EC_CURVE_ALGORITHM).generateKeyPair()

    fun decodePublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance(EC_CURVE_ALGORITHM).generatePublic(X509EncodedKeySpec(encoded))

    /** Derives one shared AES-256 key from this device's ephemeral private
     * key and the peer's ephemeral public key — identical on both ends
     * because ECDH is commutative, never transmitted itself. */
    fun deriveSessionKey(
        localKeyPair: KeyPair,
        remotePublicKey: PublicKey,
    ): SecretKeySpec {
        val sharedSecret =
            KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM).run {
                init(localKeyPair.private)
                doPhase(remotePublicKey, true)
                generateSecret()
            }
        return hkdfExpand(sharedSecret)
    }

    /** A minimal single-round HKDF-Expand (RFC 5869) — the shared secret is
     * already high-entropy (an EC point), so the HKDF-Extract step is
     * skipped in favor of using the raw secret directly as HMAC key, a
     * documented, accepted HKDF simplification when the input keying
     * material is already uniformly random. */
    private fun hkdfExpand(sharedSecret: ByteArray): SecretKeySpec {
        val mac =
            Mac.getInstance(HMAC_ALGORITHM).apply {
                init(SecretKeySpec(sharedSecret, HMAC_ALGORITHM))
            }
        val okm = mac.doFinal(HKDF_INFO.toByteArray(Charsets.UTF_8) + byteArrayOf(1))
        return SecretKeySpec(okm.copyOf(AES_KEY_BYTES), AES_ALGORITHM)
    }

    /** [associatedData] binds a ciphertext to its sequence number — a
     * replayed or reordered frame decrypts to garbage (GCM's tag check
     * fails) rather than silently succeeding. */
    fun encrypt(
        key: SecretKeySpec,
        iv: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray =
        Cipher.getInstance(AES_TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(associatedData)
            doFinal(plaintext)
        }

    fun decrypt(
        key: SecretKeySpec,
        iv: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray =
        Cipher.getInstance(AES_TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(associatedData)
            doFinal(ciphertext)
        }

    fun randomIv(): ByteArray = ByteArray(GCM_IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
}
