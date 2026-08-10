package com.wardrobe.app.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.ai.tryon.VirtualTryOnEngine
import com.wardrobe.app.core.domain.repository.VirtualTryOnRenderRepository
import com.wardrobe.app.core.image.pipeline.ImageResizer
import com.wardrobe.app.core.model.tryon.VirtualTryOnRenderOutcome
import com.wardrobe.app.core.tryon.engine.OnDeviceVirtualTryOnEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val PREVIEW_WEBP_QUALITY = 90

private sealed interface DecodedTryOnInputs {
    data class Ok(
        val bodyPhoto: Bitmap,
        val garmentCutout: Bitmap,
        val mask: Bitmap?,
    ) : DecodedTryOnInputs

    data class Failed(
        val reason: String,
    ) : DecodedTryOnInputs
}

/**
 * [VirtualTryOnRenderRepository]'s only implementation — decodes the two/
 * three input file paths, dispatches through [virtualTryOnEngine] (bound to
 * `TryOnRouter`, M12) or, when [VirtualTryOnRenderRepository.render]'s
 * `forceOnDevice` is set, [onDeviceVirtualTryOnEngine] directly, and writes
 * a successful render to a scratch cache file under `cacheDir`, never over
 * [bodyPhotoPath]/[garmentCutoutPath] themselves. Try-On is preview only:
 * nothing here ever touches a garment's permanent, persisted image.
 */
@Singleton
class VirtualTryOnRenderRepositoryImpl
    @Inject
    constructor(
        private val virtualTryOnEngine: VirtualTryOnEngine,
        private val onDeviceVirtualTryOnEngine: OnDeviceVirtualTryOnEngine,
        @ApplicationContext private val context: Context,
    ) : VirtualTryOnRenderRepository {
        // M22 fix: bitmap decode (BitmapFactory.decodeFile) and the render/
        // composite engine call previously ran on whichever dispatcher the
        // caller happened to use — in practice `feature:tryon`'s
        // `viewModelScope.launch { }`, i.e. the Main dispatcher, for two
        // full-resolution images plus on-device compositing. Dispatchers.IO
        // matches this repository layer's existing convention for file work
        // (see e.g. GarmentMaskRepositoryImpl).
        override suspend fun render(
            bodyPhotoPath: String,
            garmentCutoutPath: String,
            maskPath: String?,
            forceOnDevice: Boolean,
        ): VirtualTryOnRenderOutcome =
            withContext(Dispatchers.IO) {
                when (val decoded = decodeInputs(bodyPhotoPath, garmentCutoutPath, maskPath)) {
                    is DecodedTryOnInputs.Failed -> VirtualTryOnRenderOutcome.Failure(decoded.reason)
                    is DecodedTryOnInputs.Ok -> renderDecoded(decoded, forceOnDevice)
                }
            }

        private fun decodeInputs(
            bodyPhotoPath: String,
            garmentCutoutPath: String,
            maskPath: String?,
        ): DecodedTryOnInputs {
            val bodyPhoto = BitmapFactory.decodeFile(bodyPhotoPath)
            val garmentCutout = BitmapFactory.decodeFile(garmentCutoutPath)
            val failureReason =
                when {
                    bodyPhoto == null -> "body_photo_undecodable"
                    garmentCutout == null -> "garment_cutout_undecodable"
                    else -> null
                }
            return if (failureReason != null) {
                DecodedTryOnInputs.Failed(failureReason)
            } else {
                DecodedTryOnInputs.Ok(
                    bodyPhoto = requireNotNull(bodyPhoto),
                    garmentCutout = requireNotNull(garmentCutout),
                    mask = maskPath?.let(BitmapFactory::decodeFile),
                )
            }
        }

        private suspend fun renderDecoded(
            decoded: DecodedTryOnInputs.Ok,
            forceOnDevice: Boolean,
        ): VirtualTryOnRenderOutcome {
            val engine = if (forceOnDevice) onDeviceVirtualTryOnEngine else virtualTryOnEngine
            return when (val result = engine.render(decoded.bodyPhoto, decoded.garmentCutout, decoded.mask)) {
                is TryOnRenderResult.Success -> {
                    VirtualTryOnRenderOutcome.Success(writePreview(result), result.confidence, result.source)
                }

                is TryOnRenderResult.Failure -> {
                    VirtualTryOnRenderOutcome.Failure(result.reason)
                }
            }
        }

        private fun writePreview(result: TryOnRenderResult.Success): String {
            val file = File(context.cacheDir, "tryon_preview_${System.currentTimeMillis()}.webp")
            FileOutputStream(file).use { out ->
                ImageResizer.encodeWebpLossy(result.renderedImage, PREVIEW_WEBP_QUALITY, out)
            }
            return file.absolutePath
        }
    }
