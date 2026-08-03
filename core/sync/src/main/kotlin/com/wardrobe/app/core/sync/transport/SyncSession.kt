package com.wardrobe.app.core.sync.transport

import com.wardrobe.app.core.sync.protocol.ChangeAckBody
import com.wardrobe.app.core.sync.protocol.ChangeBatchBody
import com.wardrobe.app.core.sync.protocol.EntityChangeDto
import com.wardrobe.app.core.sync.protocol.SyncFrame
import com.wardrobe.app.core.sync.protocol.SyncMessageKind
import kotlinx.serialization.json.Json

data class SyncSessionResult(
    val receivedChanges: List<EntityChangeDto>,
    val peerAckedUpToCursor: Long,
)

/**
 * The post-handshake data exchange (Phase 8) — entirely generic over
 * [EntityChangeDto]; this module has no idea what a "garment" is. Both
 * sides exchange their own outbox batch simultaneously (full-duplex TCP
 * makes this safe: writing doesn't block on the peer having read yet), then
 * each acknowledges the cursor it received up to — `core:data` is what
 * turns that ack into `PairedDeviceEntity.lastSyncedChangeLogId` moving
 * forward, so a batch is never marked "sent" until the peer has actually
 * confirmed receiving it (resume-safe: a connection dropped before the ack
 * simply means next time's outbox query re-sends the same, still-un-acked
 * rows).
 */
class SyncSession(
    private val transport: EncryptedFrameTransport,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun exchange(
        outgoingChanges: List<EntityChangeDto>,
        outgoingCursor: Long,
    ): SyncSessionResult {
        transport.send(
            SyncFrame(
                SyncMessageKind.CHANGE_BATCH,
                json.encodeToString(ChangeBatchBody.serializer(), ChangeBatchBody(outgoingChanges, outgoingCursor)),
            ),
        )

        val peerBatchFrame = transport.receive()
        check(peerBatchFrame.kind == SyncMessageKind.CHANGE_BATCH) {
            "Expected CHANGE_BATCH but received ${peerBatchFrame.kind}"
        }
        val peerBatch = json.decodeFromString(ChangeBatchBody.serializer(), peerBatchFrame.bodyJson)

        transport.send(
            SyncFrame(
                SyncMessageKind.CHANGE_ACK,
                json.encodeToString(ChangeAckBody.serializer(), ChangeAckBody(peerBatch.cursor)),
            ),
        )
        val peerAckFrame = transport.receive()
        check(peerAckFrame.kind == SyncMessageKind.CHANGE_ACK) {
            "Expected CHANGE_ACK but received ${peerAckFrame.kind}"
        }
        val peerAck = json.decodeFromString(ChangeAckBody.serializer(), peerAckFrame.bodyJson)

        transport.send(SyncFrame(SyncMessageKind.DONE, ""))
        val doneFrame = transport.receive()
        check(doneFrame.kind == SyncMessageKind.DONE) { "Expected DONE but received ${doneFrame.kind}" }

        return SyncSessionResult(receivedChanges = peerBatch.changes, peerAckedUpToCursor = peerAck.receivedUpToCursor)
    }
}
