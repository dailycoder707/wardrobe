package com.wardrobe.app.core.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath

/**
 * Bounds Coil's own memory/disk cache (`ADR-007`: "no custom cache layer
 * invented on top" — this configures Coil's existing knobs, it doesn't
 * replace them). `WardrobeApplication` implements `SingletonImageLoader.
 * Factory` and delegates to [create] so every `AsyncImage` in the app shares
 * one bounded cache instead of Coil's unbounded defaults.
 *
 * Disk cache lives at `context.cacheDir`, deliberately separate from
 * `filesDir/images/` (see phase-5b-image-pipeline.md's storage-layout note) —
 * this is a throwaway, OS-clearable decode cache, not the source of truth for
 * any garment photo.
 */
object WardrobeImageLoaderFactory {
    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_MAX_BYTES = 64L * 1024 * 1024

    fun create(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }.build()
}
