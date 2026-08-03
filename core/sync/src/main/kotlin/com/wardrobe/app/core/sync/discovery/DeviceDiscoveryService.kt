package com.wardrobe.app.core.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SERVICE_TYPE = "_wardrobesync._tcp"

/** A paired peer found on the local network right now — the peer's current
 * IP/port, which can (and does) change between sync sessions on DHCP
 * networks, unlike its stable [com.wardrobe.app.core.model.sync.PairedDevice.deviceId]. */
data class DiscoveredDevice(
    val serviceName: String,
    val host: String,
    val port: Int,
)

/**
 * Plain `android.net.nsd.NsdManager` (mDNS/DNS-SD) — deliberately not Wi-Fi
 * Direct or a manual broadcast/socket scan: NSD is the platform-supported,
 * lowest-effort way for two apps on the same Wi-Fi network to find each
 * other's advertised service without either device needing to know the
 * other's IP address in advance. Registration/discovery are two independent
 * halves; a device does both (it advertises itself *and* looks for its
 * paired peer) since either device may initiate a sync.
 */
class DeviceDiscoveryService(
    private val context: Context,
) {
    private val nsdManager: NsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    fun registerService(port: Int): Flow<Unit> =
        callbackFlow {
            val serviceInfo =
                NsdServiceInfo().apply {
                    serviceName = SERVICE_TYPE
                    serviceType = SERVICE_TYPE
                    setPort(port)
                }
            val listener =
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {
                        trySend(Unit)
                    }

                    override fun onRegistrationFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        close()
                    }

                    override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

                    override fun onUnregistrationFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) = Unit
                }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            awaitClose { nsdManager.unregisterService(listener) }
        }

    fun discoverDevices(): Flow<DiscoveredDevice> =
        callbackFlow {
            val discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(service: NsdServiceInfo) {
                        resolveService(service)
                    }

                    override fun onServiceLost(service: NsdServiceInfo) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        close()
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit

                    private fun resolveService(service: NsdServiceInfo) {
                        nsdManager.resolveService(
                            service,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(
                                    info: NsdServiceInfo,
                                    errorCode: Int,
                                ) = Unit

                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val host = info.host?.hostAddress ?: return
                                    trySend(DiscoveredDevice(info.serviceName, host, info.port))
                                }
                            },
                        )
                    }
                }
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            awaitClose { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
}
