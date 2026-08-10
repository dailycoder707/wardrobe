package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.image.hashing.ImageHasher
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.ProcessingStage
import java.io.File
import java.io.IOException
import kotlin.math.max

private const val THUMBNAIL_SIZE_PX = 300
private const val THUMBNAIL_QUALITY = 80

/** Shared file-write helpers for [GarmentImagePipeline] — split out (Detekt's
 * `TooManyFunctions`) since [writeCutoutVariant]/[writeWhiteBackgroundVariant]
 * are only ever called from `buildVariants`, once each; [buildVariants]/
 * [writeThumbnail]/[decodeCapped] moved here too so the M10 retry entry
 * points (`GarmentImagePipelineRetry.kt`) can call them without needing
 * private access into the class itself. */

internal fun buildVariants(
    fileStore: ImageFileStore,
    originalVariant: ImageVariant,
    reconstruction: ReconstructionOutput,
    enhancement: PresentationOutcome,
    thumbnailFile: File,
    stagingDir: File,
): List<ImageVariant> =
    buildList {
        add(originalVariant)
        reconstruction.finalCutout?.let { add(writeCutoutVariant(fileStore, it, stagingDir)) }
        enhancement.whiteBackgroundVariant?.let { add(writeWhiteBackgroundVariant(fileStore, it, stagingDir)) }
        val thumbnailBitmap = BitmapFactory.decodeFile(thumbnailFile.path)
        add(thumbnailFile.toVariant(ImageType.THUMBNAIL, thumbnailBitmap = thumbnailBitmap))
    }

internal fun writeThumbnail(
    fileStore: ImageFileStore,
    source: Bitmap,
    stagingDir: File,
): File {
    val thumbnailBitmap = ImageResizer.resizeToLongEdge(source, THUMBNAIL_SIZE_PX)
    val thumbnailFile = fileStore.fileFor(stagingDir, ImageType.THUMBNAIL)
    writeOrThrow(ProcessingStage.GENERATING_THUMBNAIL) {
        thumbnailFile.outputStream().use { ImageResizer.encodeWebpLossy(thumbnailBitmap, THUMBNAIL_QUALITY, it) }
    }
    return thumbnailFile
}

/** Decodes with `inSampleSize` computed from a bounds-only pre-decode, so a
 * full-resolution camera image is never fully decoded into memory just to
 * be downscaled afterward (phase-5b-image-pipeline.md's performance
 * section). `OutOfMemoryError` is caught narrowly around this single
 * allocation, not as a blanket `catch (Throwable)`. */
internal fun decodeCapped(
    file: File,
    targetLongEdge: Int,
): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longEdge = max(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longEdge / (sampleSize * 2) >= targetLongEdge) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded =
        try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (oom: OutOfMemoryError) {
            throw ImageProcessingException(ProcessingStage.SAVING_ORIGINAL, IOException("out_of_memory", oom))
        }
    return decoded ?: throw ImageProcessingException(
        ProcessingStage.SAVING_ORIGINAL,
        IOException("decodeFile returned null for ${file.path}"),
    )
}

internal fun writeCutoutVariant(
    fileStore: ImageFileStore,
    cutout: Bitmap,
    stagingDir: File,
): ImageVariant {
    val file = fileStore.fileFor(stagingDir, ImageType.CUTOUT)
    writeOrThrow(ProcessingStage.HASHING) {
        file.outputStream().use { ImageResizer.encodeWebpLossless(cutout, it) }
    }
    return file.toVariant(ImageType.CUTOUT, cutout.width, cutout.height)
}

internal fun writeWhiteBackgroundVariant(
    fileStore: ImageFileStore,
    whiteBackground: Bitmap,
    stagingDir: File,
): ImageVariant {
    val file = fileStore.fileFor(stagingDir, ImageType.WHITE_BACKGROUND)
    writeOrThrow(ProcessingStage.HASHING) {
        file.outputStream().use { ImageResizer.encodeWebpLossless(whiteBackground, it) }
    }
    return file.toVariant(ImageType.WHITE_BACKGROUND, whiteBackground.width, whiteBackground.height)
}

internal fun File.toVariant(
    type: ImageType,
    width: Int,
    height: Int,
): ImageVariant =
    ImageVariant(
        type = type,
        filePath = path,
        width = width,
        height = height,
        fileSizeBytes = length(),
        format = if (type == ImageType.ORIGINAL) "jpg" else "webp",
        checksum = ImageHasher.sha256(this),
    )

internal fun File.toVariant(
    type: ImageType,
    thumbnailBitmap: Bitmap?,
): ImageVariant = toVariant(type, thumbnailBitmap?.width ?: 0, thumbnailBitmap?.height ?: 0)

internal inline fun writeOrThrow(
    stage: ProcessingStage,
    block: () -> Unit,
) {
    try {
        block()
    } catch (io: IOException) {
        throw ImageProcessingException(stage, io)
    }
}
