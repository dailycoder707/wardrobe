package com.wardrobe.app.core.sync.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "wardrobe_sync_device_identity"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

/**
 * Real [DeviceIdentityKeyStore] — the identity keypair lives inside
 * `AndroidKeyStore` and its private key material never leaves hardware-
 * backed storage (`setUserAuthenticationRequired(false)` deliberately: this
 * is a background sync feature, not a payment flow, and requiring a biometric
 * prompt every sync would violate "never require user interaction").
 */
class AndroidKeystoreDeviceIdentity : DeviceIdentityKeyStore {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun getOrCreatePublicKey(): PublicKey {
        keyStore.getCertificate(KEY_ALIAS)?.let { return it.publicKey }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        generator.initialize(spec)
        return generator.generateKeyPair().public
    }

    override fun sign(data: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(KEY_ALIAS, null)
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey as java.security.PrivateKey)
            update(data)
            sign()
        }
    }
}

/** A short, stable, human-comparable identifier for a public key — used
 * both as this device's own [deviceId][com.wardrobe.app.core.model.sync.PairedDevice.deviceId]
 * (derived from its own identity key, needing no separate stored id) and as
 * the fingerprint shown in Settings so a user can visually confirm which
 * physical device a paired-device entry refers to. */
fun publicKeyFingerprint(publicKey: PublicKey): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

/** Pure, testable without Keystore — verifying a signature only needs the
 * peer's already-pinned public key. */
fun verifySignature(
    publicKey: PublicKey,
    data: ByteArray,
    signature: ByteArray,
): Boolean =
    Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initVerify(publicKey)
        update(data)
        runCatching { verify(signature) }.getOrDefault(false)
    }
