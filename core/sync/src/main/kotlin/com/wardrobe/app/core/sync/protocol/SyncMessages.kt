package com.wardrobe.app.core.sync.protocol

import kotlinx.serialization.Serializable

/**
 * The wire protocol (Phase 8). Deliberately *not* a sealed-class/polymorphic
 * hierarchy — [SyncFrame] is one concrete, always-the-same-shape envelope
 * with a [kind] discriminator and a [bodyJson] string holding the kind-
 * specific body's own JSON, decoded by whichever side knows what [kind]
 * means. This trades one extra (cheap, small) layer of string-wrapping for
 * avoiding `kotlinx.serialization`'s polymorphic-module registration
 * ceremony entirely — simpler to read, simpler to test.
 */
@Serializable
enum class SyncMessageKind {
    HANDSHAKE_INIT,
    HANDSHAKE_RESPONSE,
    IMAGE_MANIFEST,
    IMAGE_REQUEST,
    IMAGE_DATA,
    IMAGE_DONE,
    CHANGE_BATCH,
    CHANGE_ACK,
    DONE,
}

@Serializable
data class SyncFrame(
    val kind: SyncMessageKind,
    val bodyJson: String,
)

/** Sent by whichever side initiated the connection (either device may
 * initiate — pairing is symmetric once both devices are known to each
 * other). [ephemeralPublicKeyBase64] is a fresh key generated for this
 * session alone; [signatureBase64] is that key signed by the sender's
 * long-term identity key, so the receiver — who already pinned the sender's
 * identity public key at pairing time — can confirm this ephemeral key
 * really came from the paired peer. */
@Serializable
data class HandshakeInitBody(
    val deviceId: String,
    val ephemeralPublicKeyBase64: String,
    val signatureBase64: String,
)

@Serializable
data class HandshakeResponseBody(
    val deviceId: String,
    val ephemeralPublicKeyBase64: String,
    val signatureBase64: String,
)

/**
 * One changed row, entirely opaque to `core:sync` itself — [fieldsJson] is
 * the entity's own serialized fields with every foreign-key column already
 * translated from a local `Long` id to the referenced row's [syncId]
 * (`core:data`'s job, not this module's — see
 * `phase-8-multi-device-sync.md`'s "Why not UUID primary keys" section for
 * why that translation has to happen at all).
 */
@Serializable
data class EntityChangeDto(
    val tableName: String,
    val syncId: String,
    val operation: String,
    val updatedAt: Long,
    val fieldsJson: String?,
)

@Serializable
data class ChangeBatchBody(
    val changes: List<EntityChangeDto>,
    val cursor: Long,
)

@Serializable
data class ChangeAckBody(
    val receivedUpToCursor: Long,
)

/**
 * Image transfer runs as its own small phase *before* [ChangeBatchBody] is
 * exchanged, so that by the time an `image_metadata` row is applied the
 * bytes it points at already exist on disk. [ImageManifestBody] is each
 * side's "here's every checksum I already have" list — reusing Phase 5b's
 * SHA-256 (`ImageHasher`) so an identical image is genuinely never resent
 * (Constitution: "never resend an identical image"). Whole-file transfer,
 * not byte-range chunked: a session interrupted mid-file simply re-sends
 * that whole file next time (the checksum diff makes this correct, if not
 * bandwidth-optimal for very large files/slow networks) — see
 * `phase-8-multi-device-sync.md`'s "Image synchronization" section for why
 * this is a stated simplification, not silently dropped scope.
 */
@Serializable
data class ImageManifestBody(
    val checksums: List<String>,
)

@Serializable
data class ImageRequestBody(
    val checksums: List<String>,
)

@Serializable
data class ImageDataBody(
    val checksum: String,
    val bytesBase64: String,
)
