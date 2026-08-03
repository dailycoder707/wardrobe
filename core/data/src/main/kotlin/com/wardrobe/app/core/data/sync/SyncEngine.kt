package com.wardrobe.app.core.data.sync

import com.wardrobe.app.core.database.dao.PairedDeviceDao
import com.wardrobe.app.core.database.dao.SyncChangeLogDao
import com.wardrobe.app.core.database.dao.SyncConflictDao
import com.wardrobe.app.core.database.dao.SyncHistoryDao
import com.wardrobe.app.core.database.entity.PairedDeviceEntity
import com.wardrobe.app.core.database.entity.SyncConflictEntity
import com.wardrobe.app.core.database.entity.SyncHistoryEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.sync.crypto.DeviceIdentityKeyStore
import com.wardrobe.app.core.sync.protocol.EntityChangeDto
import com.wardrobe.app.core.sync.transport.EncryptedFrameTransport
import com.wardrobe.app.core.sync.transport.SyncHandshake
import com.wardrobe.app.core.sync.transport.SyncSession
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Every dependency [SyncEngine] needs, bundled to stay under detekt's
 * `LongParameterList` threshold — the established "bag" pattern. */
class SyncEngineDependencies
    @Inject
    constructor(
        val registry: SyncEntityRegistry,
        val changeLogDao: SyncChangeLogDao,
        val pairedDeviceDao: PairedDeviceDao,
        val syncConflictDao: SyncConflictDao,
        val syncHistoryDao: SyncHistoryDao,
        val imageChecksumSources: ImageChecksumSources,
        val imageFileStore: ImageFileStore,
        val identityKeyStore: DeviceIdentityKeyStore,
        val clock: Clock,
    )

data class SyncRunResult(
    val changesSent: Int,
    val changesReceived: Int,
    val conflictsDetected: Int,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
)

/**
 * One full sync session against one already-paired device (Phase 8) — the
 * orchestrator every other sync component (worker, repository, Developer
 * Panel diagnostics) sits on top of. See
 * `phase-8-multi-device-sync.md`'s "Architecture" section for the full
 * session lifecycle this method runs: handshake → image transfer → change
 * batch exchange → conflict recording → cursor/history update.
 */
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val deps: SyncEngineDependencies,
    ) {
        /**
         * Catches broadly on purpose — a sync session spans network I/O,
         * handshake crypto, and arbitrary peer-supplied data, any of which
         * can fail in ways this method can't enumerate. The failure must
         * still be recorded to sync history before propagating, so callers
         * (the worker, manual sync) see a real error rather than a hang.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun runSession(
            peer: PairedDeviceEntity,
            inputStream: InputStream,
            outputStream: OutputStream,
            isInitiator: Boolean,
        ): SyncRunResult {
            val localDeviceId =
                com.wardrobe.app.core.sync.crypto.publicKeyFingerprint(
                    deps.identityKeyStore.getOrCreatePublicKey(),
                )
            val startedAt = deps.clock.millis()
            return try {
                val sessionKey = performHandshake(peer, inputStream, outputStream, localDeviceId, isInitiator)
                val transport = EncryptedFrameTransport(inputStream, outputStream, sessionKey)
                runImageTransferPhase(transport, deps.imageChecksumSources, deps.imageFileStore)
                val result = runChangeExchange(transport, peer)
                recordHistory(startedAt, result, failed = false)
                result
            } catch (error: Exception) {
                recordHistory(startedAt, SyncRunResult(0, 0, 0), failed = true, error = error.message)
                throw error
            }
        }

        private fun performHandshake(
            peer: PairedDeviceEntity,
            inputStream: InputStream,
            outputStream: OutputStream,
            localDeviceId: String,
            isInitiator: Boolean,
        ): javax.crypto.spec.SecretKeySpec {
            val handshake = SyncHandshake(deps.identityKeyStore, localDeviceId)
            val resolvePeerKey: (String) -> PublicKey? = { deviceId ->
                if (deviceId == peer.deviceId) decodePublicKey(peer.publicKeyBase64) else null
            }
            return if (isInitiator) {
                handshake.performAsInitiator(inputStream, outputStream, resolvePeerKey)
            } else {
                handshake.performAsResponder(inputStream, outputStream, resolvePeerKey)
            }
        }

        private suspend fun runChangeExchange(
            transport: EncryptedFrameTransport,
            peer: PairedDeviceEntity,
        ): SyncRunResult {
            val cursor = peer.lastSyncedChangeLogId
            val pendingRows = deps.changeLogDao.changesSince(cursor)
            val latestId = deps.changeLogDao.latestId() ?: cursor
            val outgoing =
                pendingRows.mapNotNull { row ->
                    buildOutgoingChange(row.tableName, row.syncId, row.operation)
                }

            val session = SyncSession(transport)
            val result = session.exchange(outgoing, latestId)

            var conflicts = 0
            result.receivedChanges.forEach { change ->
                if (applyIncomingChange(change) is ApplyOutcome.EditDeleteConflict) conflicts++
            }

            deps.pairedDeviceDao.updateSyncCursor(peer.deviceId, deps.clock.millis(), latestId)
            deps.changeLogDao.compact()

            return SyncRunResult(
                changesSent = outgoing.size,
                changesReceived = result.receivedChanges.size,
                conflictsDetected = conflicts,
                bytesSent = transport.totalBytesSent,
                bytesReceived = transport.totalBytesReceived,
            )
        }

        private suspend fun buildOutgoingChange(
            tableName: String,
            syncId: String,
            operation: String,
        ): EntityChangeDto? {
            val handler = deps.registry.handlerFor(tableName)
            val fieldsJson =
                if (operation == "DELETE" || handler == null) null else handler.currentFieldsJson(syncId)
            val unresolvable = handler == null || (operation != "DELETE" && fieldsJson == null)
            return if (unresolvable) {
                null
            } else if (operation == "DELETE") {
                EntityChangeDto(tableName, syncId, "DELETE", deps.clock.millis(), null)
            } else {
                EntityChangeDto(tableName, syncId, "UPSERT", deps.clock.millis(), fieldsJson)
            }
        }

        private suspend fun resolveIncomingOutcome(
            handler: SyncEntityHandler,
            change: EntityChangeDto,
        ): ApplyOutcome {
            val fieldsJson = change.fieldsJson
            return if (change.operation == "DELETE") {
                handler.applyDelete(change.syncId, change.updatedAt)
            } else if (fieldsJson == null) {
                ApplyOutcome.LocalNewerIgnored
            } else {
                handler.applyUpsert(change.syncId, fieldsJson, change.updatedAt, deps.registry.resolver)
            }
        }

        private suspend fun applyIncomingChange(change: EntityChangeDto): ApplyOutcome {
            val handler = deps.registry.handlerFor(change.tableName)
            val outcome =
                if (handler == null) ApplyOutcome.LocalNewerIgnored else resolveIncomingOutcome(handler, change)
            if (outcome is ApplyOutcome.EditDeleteConflict) {
                deps.syncConflictDao.insert(
                    SyncConflictEntity(
                        entityType = change.tableName,
                        entitySyncId = change.syncId,
                        reason = "EDIT_DELETE_CONFLICT",
                        localSummary = outcome.localSummary,
                        remoteSummary = outcome.remoteSummary,
                        detectedAt = deps.clock.millis(),
                    ),
                )
            }
            return outcome
        }

        private suspend fun recordHistory(
            startedAt: Long,
            result: SyncRunResult,
            failed: Boolean,
            error: String? = null,
        ) {
            deps.syncHistoryDao.insert(
                SyncHistoryEntity(
                    startedAt = startedAt,
                    finishedAt = deps.clock.millis(),
                    outcome = if (failed) "FAILED" else "SUCCESS",
                    changesSent = result.changesSent,
                    changesReceived = result.changesReceived,
                    bytesSent = result.bytesSent,
                    bytesReceived = result.bytesReceived,
                    conflictsDetected = result.conflictsDetected,
                    errorMessage = error,
                ),
            )
        }

        private fun decodePublicKey(base64: String): PublicKey {
            val bytes = Base64.getDecoder().decode(base64)
            return java.security.KeyFactory
                .getInstance("EC")
                .generatePublic(X509EncodedKeySpec(bytes))
        }
    }
