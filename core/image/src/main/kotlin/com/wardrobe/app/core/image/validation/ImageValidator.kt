package com.wardrobe.app.core.image.validation

import android.graphics.BitmapFactory
import java.io.File

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(
        val reason: String,
    ) : ValidationResult
}

/**
 * Rejects unusable files *before* any full-resolution decode is attempted —
 * distinct from [com.wardrobe.app.core.image.quality.ImageQualityAnalyzer],
 * which gives soft warnings on usable-but-suboptimal photos. This is a hard
 * reject: corrupted files, unsupported formats, empty files, and images too
 * small to be a real garment photo (not the same threshold as the quality
 * analyzer's "low resolution" *warning* — this floor is about "can this even
 * be processed," that one is about "is this a good photo").
 */
object ImageValidator {
    private const val MIN_DIMENSION_PX = 200

    private val SUPPORTED_MIME_TYPES =
        setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    private data class Check(
        val failed: Boolean,
        val reason: String,
    )

    fun validate(file: File): ValidationResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (file.exists() && file.length() > 0L) {
            BitmapFactory.decodeFile(file.path, bounds)
        }

        // outMimeType can be null on some OS versions even for a decodable file
        // (observed inconsistently across BitmapFactory implementations) — only
        // reject when it's present and unrecognized, never on a null mime type
        // alone, since that would produce false-positive rejections of otherwise
        // valid photos.
        val mimeType = bounds.outMimeType
        val checks =
            listOf(
                Check(!file.exists() || file.length() == 0L, "file_missing_or_empty"),
                Check(bounds.outWidth <= 0 || bounds.outHeight <= 0, "corrupted_or_undecodable"),
                Check(mimeType != null && mimeType !in SUPPORTED_MIME_TYPES, "unsupported_format:$mimeType"),
                Check(
                    bounds.outWidth < MIN_DIMENSION_PX || bounds.outHeight < MIN_DIMENSION_PX,
                    "dimensions_too_small",
                ),
            )

        val failure = checks.firstOrNull { it.failed }
        return if (failure != null) ValidationResult.Invalid(failure.reason) else ValidationResult.Valid
    }
}
