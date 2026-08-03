package com.wardrobe.app.core.sync.crypto

import java.security.PublicKey

/**
 * This device's own long-term identity keypair — used only to *sign*
 * ephemeral session keys (never for the actual data encryption itself, see
 * [SessionCrypto]). Deliberately an interface: the real implementation
 * ([AndroidKeystoreDeviceIdentity]) generates a non-exportable key inside
 * `AndroidKeyStore`, which isn't available under a plain JVM/Robolectric
 * test — tests substitute a plain [java.security.KeyPairGenerator]-backed
 * fake instead, the same seam pattern this project already uses for
 * `DeviceLocationSource`/`WeatherProvider`.
 *
 * Why signing, not key agreement: `AndroidKeyStore`'s `PURPOSE_AGREE_KEY`
 * (letting a keystore-backed key do ECDH directly) only exists from API 31 —
 * this app's `minSdk` is 26. Signing (`PURPOSE_SIGN`) has been available
 * since API 23, so the identity key stays hardware-backed and non-exportable
 * on every supported device by using it only to *authenticate* a fresh,
 * software-generated ephemeral keypair each session — see
 * `phase-8-multi-device-sync.md`'s "Encryption" section for the full
 * handshake this enables.
 */
interface DeviceIdentityKeyStore {
    /** Stable across app restarts; generated once on first use. */
    fun getOrCreatePublicKey(): PublicKey

    fun sign(data: ByteArray): ByteArray
}
