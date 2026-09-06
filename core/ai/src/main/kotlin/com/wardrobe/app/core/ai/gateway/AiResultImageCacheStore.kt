package com.wardrobe.app.core.ai.gateway

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.image.pipeline.ImageResizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_SUBDIRECTORY = "ai_result_cache"

/**
 * Cached image-task results (Extraction/Reconstruction/Try-On) are stored
 * as files, not as bytes inside the `ai_result_cache` row — the same "Room
 * stores a path, not a blob" convention `image_metadata` already uses. This
 * directory is app-cache (not files-dir): it's a derived, regenerable
 * result, safe for the OS to reclaim under storage pressure — a cache miss
 * just re-dispatches instead of losing data.
 */
@Singleton
class AiResultImageCacheStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun save(
            bitmap: Bitmap,
            cacheKeyHash: String,
        ): String {
            val directory = File(context.cacheDir, CACHE_SUBDIRECTORY).apply { mkdirs() }
            val file = File(directory, "$cacheKeyHash.webp")
            file.outputStream().use { ImageResizer.encodeWebpLossless(bitmap, it) }
            return file.absolutePath
        }

        fun load(filePath: String): Bitmap? = BitmapFactory.decodeFile(filePath)
    }
