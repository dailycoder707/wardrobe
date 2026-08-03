package com.wardrobe.app.core.data.sync

import android.content.Context
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.dao.PairedDeviceDao
import com.wardrobe.app.core.database.dao.SyncChangeLogDao
import com.wardrobe.app.core.database.dao.SyncConflictDao
import com.wardrobe.app.core.database.dao.SyncHistoryDao
import com.wardrobe.app.core.database.entity.PairedDeviceEntity
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.PairedDevice
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncEntityType
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncOutcome
import com.wardrobe.app.core.model.sync.SyncState
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import com.wardrobe.app.core.sync.discovery.DeviceDiscoveryService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ServerSocket
import java.net.Socket
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val DISCOVERY_TIMEOUT_MS = 20_000L

/** Everything [SyncRepositoryImpl] needs, bundled to stay under detekt's
 * `LongParameterList` threshold. */
class SyncEngineComponents
    @Inject
    constructor(
        val syncEngine: SyncEngine,
        val discoveryService: DeviceDiscoveryService,
        val pairedDeviceDao: PairedDeviceDao,
        val changeLogDao: SyncChangeLogDao,
        val syncConflictDao: SyncConflictDao,
        val syncHistoryDao: SyncHistoryDao,
        val imageFileStore: ImageFileStore,
        @ApplicationContext val context: Context,
        val clock: Clock,
    )

/**
 * The one-paired-device sync orchestrator (Phase 8) — this app's pairing
 * model is one tablet + one phone, so "the currently paired device" (first
 * row, if any) is what [syncNow] targets. Nothing in the schema hardcodes a
 * count of one, but this repository does, deliberately, to keep discovery
 * simple: it doesn't need to disambiguate *which* paired peer answered.
 */
@Singleton
class SyncRepositoryImpl
    @Inject
    constructor(
        private val components: SyncEngineComponents,
    ) : SyncRepository {
        private val state = MutableStateFlow(SyncState.IDLE)
        private val lastError = MutableStateFlow<String?>(null)

        override fun observeStatus(): Flow<SyncStatusSnapshot> =
            combine(
                components.pairedDeviceDao.observeAll(),
                state,
                lastError,
            ) { devices, currentState, error ->
                val device = devices.firstOrNull()
                val pendingCount =
                    device?.let { components.changeLogDao.countSince(it.lastSyncedChangeLogId) } ?: 0
                SyncStatusSnapshot(
                    state = currentState,
                    connectedDevice = device?.toDomain(),
                    lastSyncAt = device?.lastSyncAt?.let(Instant::ofEpochMilli),
                    pendingChangeCount = pendingCount,
                    storageUsedBytes = computeStorageUsedBytes(),
                    lastError = error,
                )
            }

        private fun computeStorageUsedBytes(): Long {
            val dbFile = components.context.getDatabasePath(WardrobeDatabase.DATABASE_NAME)
            val imagesBytes = components.imageFileStore.allImageFilesOnDisk().sumOf { it.length() }
            return dbFile.length() + imagesBytes
        }

        /**
         * Catches broadly on purpose — [raceToConnectAndSync] spans NSD
         * discovery, raw sockets, and [SyncEngine.runSession], any of which
         * can fail for reasons outside this method's control (peer
         * unreachable, timeout, malformed frame). The UI must see
         * [SyncState.ERROR] rather than an unhandled crash.
         */
        @Suppress("TooGenericExceptionCaught")
        override suspend fun syncNow() {
            val peer =
                components.pairedDeviceDao
                    .observeAll()
                    .first()
                    .firstOrNull() ?: return
            state.value = SyncState.DISCOVERING
            lastError.value = null
            try {
                raceToConnectAndSync(peer)
                state.value = SyncState.IDLE
            } catch (error: Exception) {
                lastError.value = error.message
                state.value = SyncState.ERROR
            }
        }

        /** Both roles run concurrently with a shared timeout — whichever
         * completes a connection first wins, the other is cancelled. See
         * `phase-8-multi-device-sync.md`'s "Local sync"/"Known limitations"
         * sections for why this best-effort race (not a persistent
         * listener) is this phase's chosen tradeoff under WorkManager's
         * short-lived execution model. */
        private suspend fun raceToConnectAndSync(peer: PairedDeviceEntity) {
            withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                CoroutineScope(Dispatchers.IO + coroutineContext).let { scope ->
                    val responderJob = scope.launchResponder(peer)
                    val initiatorJob = scope.launchInitiator(peer)
                    select<Unit> {
                        responderJob.onAwait { }
                        initiatorJob.onAwait { }
                    }
                    responderJob.cancel()
                    initiatorJob.cancel()
                }
            }
        }

        private fun CoroutineScope.launchResponder(peer: PairedDeviceEntity) =
            async {
                runCatching {
                    ServerSocket(0).use { serverSocket ->
                        val registrationJob =
                            launch { components.discoveryService.registerService(serverSocket.localPort).collect {} }
                        state.value = SyncState.CONNECTING
                        try {
                            serverSocket.accept().use { socket ->
                                runSessionAndRecord(peer, socket, isInitiator = false)
                            }
                        } finally {
                            registrationJob.cancel()
                        }
                    }
                }
            }

        private fun CoroutineScope.launchInitiator(peer: PairedDeviceEntity) =
            async {
                runCatching {
                    val discovered = components.discoveryService.discoverDevices().firstOrNull()
                    if (discovered != null) {
                        state.value = SyncState.CONNECTING
                        Socket(discovered.host, discovered.port).use { socket ->
                            runSessionAndRecord(peer, socket, isInitiator = true)
                        }
                    }
                }
            }

        private suspend fun runSessionAndRecord(
            peer: PairedDeviceEntity,
            socket: Socket,
            isInitiator: Boolean,
        ) {
            state.value = SyncState.SYNCING
            components.syncEngine.runSession(peer, socket.getInputStream(), socket.getOutputStream(), isInitiator)
        }

        override fun observeUnresolvedConflicts(): Flow<List<SyncConflict>> =
            components.syncConflictDao.observeUnresolved().map { list -> list.map { it.toDomain() } }

        override suspend fun resolveConflict(
            conflictId: Long,
            resolution: ConflictResolution,
        ) {
            components.syncConflictDao.resolve(conflictId, components.clock.millis(), resolution.name)
        }

        override fun observeHistory(limit: Int): Flow<List<SyncHistoryEntry>> =
            components.syncHistoryDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
    }

private fun PairedDeviceEntity.toDomain() =
    PairedDevice(
        deviceId = deviceId,
        displayName = displayName,
        publicKeyFingerprint = publicKeyFingerprint,
        pairedAt = Instant.ofEpochMilli(pairedAt),
        lastSyncAt = lastSyncAt?.let(Instant::ofEpochMilli),
    )

private fun com.wardrobe.app.core.database.entity.SyncConflictEntity.toDomain() =
    SyncConflict(
        id = id,
        entityType =
            runCatching { SyncEntityType.valueOf(entityType.uppercase()) }.getOrDefault(SyncEntityType.GARMENT),
        entitySyncId = entitySyncId,
        reason = com.wardrobe.app.core.model.sync.ConflictReason.EDIT_DELETE_CONFLICT,
        localSummary = localSummary,
        remoteSummary = remoteSummary,
        detectedAt = Instant.ofEpochMilli(detectedAt),
        resolvedAt = resolvedAt?.let(Instant::ofEpochMilli),
        resolution = resolution?.let { runCatching { ConflictResolution.valueOf(it) }.getOrNull() },
    )

private fun com.wardrobe.app.core.database.entity.SyncHistoryEntity.toDomain() =
    SyncHistoryEntry(
        id = id,
        startedAt = Instant.ofEpochMilli(startedAt),
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
        outcome = runCatching { SyncOutcome.valueOf(outcome) }.getOrDefault(SyncOutcome.FAILED),
        changesSent = changesSent,
        changesReceived = changesReceived,
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        conflictsDetected = conflictsDetected,
        errorMessage = errorMessage,
    )
