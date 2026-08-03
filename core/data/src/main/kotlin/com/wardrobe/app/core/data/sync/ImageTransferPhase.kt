package com.wardrobe.app.core.data.sync

import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.GarmentMaskDao
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.sync.protocol.ImageDataBody
import com.wardrobe.app.core.sync.protocol.ImageManifestBody
import com.wardrobe.app.core.sync.protocol.ImageRequestBody
import com.wardrobe.app.core.sync.protocol.SyncFrame
import com.wardrobe.app.core.sync.protocol.SyncMessageKind
import com.wardrobe.app.core.sync.transport.EncryptedFrameTransport
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

private val imageJson = Json { ignoreUnknownKeys = true }

/** Every checksum-bearing image source this phase pools into one manifest —
 * garment photos (Phase 8), plus Phase 10's body reference photos and
 * garment masks. A bag, not three parameters, for the same
 * `LongParameterList`-avoidance reason every other "bag of dependencies"
 * in this codebase exists. */
class ImageChecksumSources
    @javax.inject.Inject
    constructor(
        val imageMetadataDao: ImageMetadataDao,
        val bodyProfileDao: BodyProfileDao,
        val garmentMaskDao: GarmentMaskDao,
    )

/**
 * Runs before any image-bearing row is applied — both sides exchange the
 * checksums they already have (across every source in [sources]), then each
 * requests only the checksums it's missing and receives those files whole,
 * staged by checksum (final destination isn't known until the corresponding
 * row is applied — see [ImageMetadataSyncHandler]'s KDoc, and Phase 10's
 * `BodyProfileSyncHandler`/`GarmentMaskSyncHandler`, which follow the exact
 * same pattern for body photos/masks). See `protocol/SyncMessages.kt`'s
 * `ImageManifestBody` KDoc for why this is whole-file, not byte-range
 * resumable, transfer.
 */
suspend fun runImageTransferPhase(
    transport: EncryptedFrameTransport,
    sources: ImageChecksumSources,
    imageFileStore: ImageFileStore,
) {
    val localChecksums = allLocalChecksums(sources)
    val manifest = ImageManifestBody(localChecksums.toList())
    transport.send(
        SyncFrame(SyncMessageKind.IMAGE_MANIFEST, imageJson.encodeToString(ImageManifestBody.serializer(), manifest)),
    )

    val peerManifestFrame = transport.receive()
    check(peerManifestFrame.kind == SyncMessageKind.IMAGE_MANIFEST) { "Expected IMAGE_MANIFEST" }
    val peerManifest = imageJson.decodeFromString(ImageManifestBody.serializer(), peerManifestFrame.bodyJson)
    val missingLocally = peerManifest.checksums.filterNot { it in localChecksums }

    val request = ImageRequestBody(missingLocally)
    transport.send(
        SyncFrame(SyncMessageKind.IMAGE_REQUEST, imageJson.encodeToString(ImageRequestBody.serializer(), request)),
    )

    val peerRequestFrame = transport.receive()
    check(peerRequestFrame.kind == SyncMessageKind.IMAGE_REQUEST) { "Expected IMAGE_REQUEST" }
    val peerRequest = imageJson.decodeFromString(ImageRequestBody.serializer(), peerRequestFrame.bodyJson)

    sendRequestedImages(transport, sources, peerRequest.checksums)
    receiveRequestedImages(transport, missingLocally.size, imageFileStore)
}

private suspend fun allLocalChecksums(sources: ImageChecksumSources): Set<String> =
    (
        sources.imageMetadataDao.getAllChecksums() +
            sources.bodyProfileDao.getAllPhotoChecksums() +
            sources.garmentMaskDao.getAllChecksums()
    ).toSet()

/** Which local file (if any) currently holds this checksum's bytes, tried
 * across every image-bearing source in turn — the first match wins, since
 * a checksum is content-addressed and any source's copy is equally valid
 * to send. */
private suspend fun localFileForChecksum(
    sources: ImageChecksumSources,
    checksum: String,
): File? =
    sources.imageMetadataDao
        .getByChecksum(checksum)
        ?.filePath
        ?.let(::File)
        ?: sources.bodyProfileDao
            .getPhotoByChecksum(checksum)
            ?.filePath
            ?.let(::File)
        ?: sources.garmentMaskDao
            .getByChecksum(checksum)
            ?.maskFilePath
            ?.let(::File)

private suspend fun sendRequestedImages(
    transport: EncryptedFrameTransport,
    sources: ImageChecksumSources,
    requestedChecksums: List<String>,
) {
    requestedChecksums.forEach { checksum ->
        val file = localFileForChecksum(sources, checksum)
        if (file != null && file.exists()) {
            val body = ImageDataBody(checksum, Base64.getEncoder().encodeToString(file.readBytes()))
            transport.send(
                SyncFrame(SyncMessageKind.IMAGE_DATA, imageJson.encodeToString(ImageDataBody.serializer(), body)),
            )
        }
    }
    transport.send(SyncFrame(SyncMessageKind.IMAGE_DONE, ""))
}

/** [expectedCount] bounds how many [SyncMessageKind.IMAGE_DATA] frames to
 * expect before the terminating [SyncMessageKind.IMAGE_DONE] — the peer may
 * send fewer if a file went missing on its end since the manifest was built
 * (e.g. a deleted garment's photo). Each received file is written to
 * [ImageFileStore.syncStagingFile], ready for
 * [ImageMetadataSyncHandler][com.wardrobe.app.core.data.sync.handlers.ImageMetadataSyncHandler]
 * to move into its final garment/type location once the change batch phase
 * knows which garment it belongs to. */
private suspend fun receiveRequestedImages(
    transport: EncryptedFrameTransport,
    expectedCount: Int,
    imageFileStore: ImageFileStore,
) {
    repeat(expectedCount) {
        val frame = transport.receive()
        if (frame.kind == SyncMessageKind.IMAGE_DONE) return
        check(frame.kind == SyncMessageKind.IMAGE_DATA) { "Expected IMAGE_DATA but received ${frame.kind}" }
        val body = imageJson.decodeFromString(ImageDataBody.serializer(), frame.bodyJson)
        val stagingFile = imageFileStore.syncStagingFile(body.checksum)
        stagingFile.parentFile?.mkdirs()
        stagingFile.writeBytes(Base64.getDecoder().decode(body.bytesBase64))
    }
    val doneFrame = transport.receive()
    check(doneFrame.kind == SyncMessageKind.IMAGE_DONE) { "Expected IMAGE_DONE but received ${doneFrame.kind}" }
}
