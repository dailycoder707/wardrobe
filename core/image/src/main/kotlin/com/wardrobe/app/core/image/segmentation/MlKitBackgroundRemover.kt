package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The provisional default chosen this phase — see phase-5b-image-pipeline.md's
 * "Background removal" section and `TECHNICAL_DEBT.md` for why this is a
 * reasoned choice rather than a spike-verified one. Nothing outside this file
 * and [BackgroundRemoverModule] knows which implementation is bound
 * (ADR-008) — swapping to a bundled TFLite model later doesn't touch
 * `GarmentImagePipeline` or anything above it.
 */
@Singleton
class MlKitBackgroundRemover
    @Inject
    constructor() : BackgroundRemover {
        private val segmenter by lazy {
            val options =
                SubjectSegmenterOptions
                    .Builder()
                    .enableForegroundBitmap()
                    .enableForegroundConfidenceMask()
                    .build()
            SubjectSegmentation.getClient(options)
        }

        override suspend fun removeBackground(bitmap: Bitmap): CutoutResult =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                segmenter
                    .process(input)
                    .addOnSuccessListener { result ->
                        val foreground = result.foregroundBitmap
                        if (foreground == null) {
                            continuation.resume(CutoutResult.Failure("mlkit_no_foreground_bitmap"))
                            return@addOnSuccessListener
                        }
                        val confidence = result.foregroundConfidenceMask?.let(::averageConfidence)
                        continuation.resume(CutoutResult.Success(foreground, confidence))
                    }.addOnFailureListener { exception ->
                        continuation.resume(
                            CutoutResult.Failure(exception.message ?: "mlkit_segmentation_failed"),
                        )
                    }
            }

        /** A real average of ML Kit's own per-pixel confidence mask — never a
         * fabricated number (Constitution rule 4). */
        private fun averageConfidence(mask: java.nio.FloatBuffer): Float {
            val duplicate = mask.duplicate()
            duplicate.rewind()
            var sum = 0f
            var count = 0
            while (duplicate.hasRemaining()) {
                sum += duplicate.get()
                count++
            }
            return if (count == 0) 0f else sum / count
        }
    }
