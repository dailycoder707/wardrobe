package com.wardrobe.app.core.tryon.rendercache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.wardrobe.app.core.tryon.rendering.TRY_ON_LAYER_WIDTH_FRACTION
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flattens a body photo + garment layer stack into a single bitmap via
 * plain `Canvas` draw calls (needed because this runs off the UI thread,
 * unlike the live interactive `feature:tryon` `TryOnScreen`, which always
 * composites via Compose `graphicsLayer`s and never reads from this cache,
 * since it must reflect in-progress drags immediately). Used only for
 * non-interactive preview contexts — e.g. a Saved Looks grid thumbnail.
 *
 * A small in-memory map keyed by [TryOnRenderCacheKey.digest], not a disk
 * cache: this only ever needs to hold a handful of recently-viewed
 * thumbnails, and every input is already cheap to re-decode from disk if
 * evicted (process death loses the cache, which just means the next
 * thumbnail render recomputes once).
 */
@Singleton
class TryOnRenderCache
    @Inject
    constructor() {
        private val cachedByDigest = mutableMapOf<String, Bitmap>()

        /** Returns the cached flattened bitmap if [key]'s digest matches an
         * entry already computed, otherwise renders, caches, and returns a
         * fresh one. `null` only when [backgroundPhotoPath] itself can't be
         * decoded (no body profile photo on disk). */
        fun render(
            backgroundPhotoPath: String,
            key: TryOnRenderCacheKey,
        ): Bitmap? {
            val digest = key.digest()
            val cached = cachedByDigest[digest]
            val background = if (cached != null) null else BitmapFactory.decodeFile(backgroundPhotoPath)
            return when {
                cached != null -> cached
                background == null -> null
                else -> flatten(background, key.layers).also { cachedByDigest[digest] = it }
            }
        }

        /** Clears every cached thumbnail — a full clear rather than tracking
         * per-garment reverse-dependencies, matching this cache's own small,
         * short-lived scope. Callers don't need this for correctness
         * ([render]'s own digest mismatch already invalidates automatically)
         * — only to reclaim memory proactively, e.g. after a bulk garment
         * import. */
        fun invalidateAll() {
            cachedByDigest.clear()
        }

        private fun flatten(
            background: Bitmap,
            layers: List<TryOnRenderLayerInput>,
        ): Bitmap {
            val result = background.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            layers.forEach { layer -> drawLayer(canvas, result, layer, paint) }
            return result
        }

        private fun drawLayer(
            canvas: Canvas,
            background: Bitmap,
            layer: TryOnRenderLayerInput,
            paint: Paint,
        ) {
            val cutout = BitmapFactory.decodeFile(layer.maskFilePath ?: layer.cutoutFilePath) ?: return
            val targetWidth = background.width * TRY_ON_LAYER_WIDTH_FRACTION
            val baseScale = targetWidth / cutout.width
            val effectiveScale = baseScale * layer.scale
            val matrix =
                Matrix().apply {
                    postScale(effectiveScale, effectiveScale)
                    postRotate(layer.rotationDegrees)
                    postTranslate(
                        layer.offsetXFraction * background.width - (cutout.width * effectiveScale) / 2f,
                        layer.offsetYFraction * background.height,
                    )
                }
            canvas.drawBitmap(cutout, matrix, paint)
        }
    }
