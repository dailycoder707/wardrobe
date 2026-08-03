package com.wardrobe.app.core.data.sync

import android.graphics.Bitmap
import android.os.Build
import com.wardrobe.app.core.database.dao.PairedDeviceDao
import com.wardrobe.app.core.database.entity.PairedDeviceEntity
import com.wardrobe.app.core.domain.repository.DevicePairingRepository
import com.wardrobe.app.core.domain.repository.PairingOfferImage
import com.wardrobe.app.core.model.sync.PairedDevice
import com.wardrobe.app.core.sync.crypto.DeviceIdentityKeyStore
import com.wardrobe.app.core.sync.crypto.publicKeyFingerprint
import com.wardrobe.app.core.sync.pairing.PairingExchange
import com.wardrobe.app.core.sync.pairing.PairingOfferPayload
import com.wardrobe.app.core.sync.pairing.PairingQrCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val PNG_QUALITY = 100

@Singleton
class DevicePairingRepositoryImpl
    @Inject
    constructor(
        private val pairedDeviceDao: PairedDeviceDao,
        private val identityKeyStore: DeviceIdentityKeyStore,
        private val clock: Clock,
        private val applicationScope: CoroutineScope,
    ) : DevicePairingRepository {
        private val json = Json { ignoreUnknownKeys = true }
        private var activeServerSocket: ServerSocket? = null

        override suspend fun generatePairingOfferImage(): PairingOfferImage =
            withContext(Dispatchers.IO) {
                cancelPairingOffer()
                val serverSocket = ServerSocket(0)
                activeServerSocket = serverSocket
                val publicKey = identityKeyStore.getOrCreatePublicKey()
                val localDeviceId = publicKeyFingerprint(publicKey)
                val token = UUID.randomUUID().toString()
                val payload =
                    PairingOfferPayload(
                        deviceId = localDeviceId,
                        displayName = Build.MODEL ?: "Wardrobe device",
                        identityPublicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded),
                        pairingToken = token,
                        hostAddress = resolveLocalIpAddress() ?: "0.0.0.0",
                        hostPort = serverSocket.localPort,
                    )
                applicationScope.launch(Dispatchers.IO) { acceptIncomingPairing(serverSocket, payload) }
                bitmapToPng(PairingQrCodec.encode(payload))
            }

        override suspend fun cancelPairingOffer() {
            withContext(Dispatchers.IO) {
                runCatching { activeServerSocket?.close() }
                activeServerSocket = null
            }
        }

        private suspend fun acceptIncomingPairing(
            serverSocket: ServerSocket,
            payload: PairingOfferPayload,
        ) {
            val result =
                runCatching {
                    serverSocket.accept().use { socket ->
                        PairingExchange.acceptIncoming(
                            inputStream = socket.getInputStream(),
                            outputStream = socket.getOutputStream(),
                            expectedToken = payload.pairingToken,
                            localDeviceId = payload.deviceId,
                            localDisplayName = payload.displayName,
                            localIdentityPublicKeyBase64 = payload.identityPublicKeyBase64,
                        )
                    }
                }.getOrNull() ?: return
            persistPairedDevice(result.deviceId, result.displayName, result.identityPublicKeyBase64)
        }

        override suspend fun completePairing(scannedQrText: String): Result<PairedDevice> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val offer = json.decodeFromString(PairingOfferPayload.serializer(), scannedQrText)
                    val publicKey = identityKeyStore.getOrCreatePublicKey()
                    val localDeviceId = publicKeyFingerprint(publicKey)
                    val result =
                        java.net.Socket(offer.hostAddress, offer.hostPort).use { socket ->
                            PairingExchange.connectAndConfirm(
                                inputStream = socket.getInputStream(),
                                outputStream = socket.getOutputStream(),
                                offer = offer,
                                localDeviceId = localDeviceId,
                                localDisplayName = Build.MODEL ?: "Wardrobe device",
                                localIdentityPublicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded),
                            )
                        }
                    persistPairedDevice(result.deviceId, result.displayName, result.identityPublicKeyBase64)
                }
            }

        private suspend fun persistPairedDevice(
            deviceId: String,
            displayName: String,
            identityPublicKeyBase64: String,
        ): PairedDevice {
            val fingerprint = publicKeyFingerprint(decodePublicKey(identityPublicKeyBase64))
            val now = clock.millis()
            pairedDeviceDao.upsert(
                PairedDeviceEntity(
                    deviceId = deviceId,
                    displayName = displayName,
                    publicKeyFingerprint = fingerprint,
                    publicKeyBase64 = identityPublicKeyBase64,
                    pairedAt = now,
                    lastSyncAt = null,
                ),
            )
            return PairedDevice(deviceId, displayName, fingerprint, Instant.ofEpochMilli(now), null)
        }

        override fun observePairedDevices(): Flow<List<PairedDevice>> =
            pairedDeviceDao.observeAll().map { entities ->
                entities.map {
                    PairedDevice(
                        deviceId = it.deviceId,
                        displayName = it.displayName,
                        publicKeyFingerprint = it.publicKeyFingerprint,
                        pairedAt = Instant.ofEpochMilli(it.pairedAt),
                        lastSyncAt = it.lastSyncAt?.let(Instant::ofEpochMilli),
                    )
                }
            }

        override suspend fun unpairDevice(deviceId: String) = pairedDeviceDao.deleteById(deviceId)

        private fun decodePublicKey(base64: String): java.security.PublicKey {
            val bytes = Base64.getDecoder().decode(base64)
            val spec = java.security.spec.X509EncodedKeySpec(bytes)
            return java.security.KeyFactory
                .getInstance("EC")
                .generatePublic(spec)
        }

        private fun bitmapToPng(bitmap: Bitmap): ByteArray =
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
                stream.toByteArray()
            }

        /** The first non-loopback IPv4 address on any active interface —
         * good enough for a device on an ordinary home Wi-Fi network; see
         * `phase-8-multi-device-sync.md`'s Known Limitations for when this
         * heuristic can pick the wrong interface (VPNs, multiple active
         * networks). */
        private fun resolveLocalIpAddress(): String? =
            NetworkInterface
                .getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
    }
