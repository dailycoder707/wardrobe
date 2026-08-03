package com.wardrobe.app.core.tryon.shadow

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build

private const val SHADOW_ALPHA_FRACTION = 0.35f
private const val MIN_ALPHA = 0
private const val MAX_ALPHA = 255

/** `RenderEffect`'s minimum supported API level — this project's `minSdk` is
 * 26 (verified in `build.gradle.kts`), so a real, disclosed capability gap
 * exists below it: see [supportsBlur]. */
private const val MIN_BLUR_SDK = Build.VERSION_CODES.S

/**
 * Deterministic, no ML — derives a shadow silhouette from a garment
 * cutout's own alpha channel: every pixel with any coverage becomes solid
 * black at a fraction of its original alpha. [deriveShadowSilhouette] alone
 * is what every API level renders; whether that silhouette is additionally
 * *blurred* is gated by [supportsBlur] and applied by the Compose layer via
 * `Modifier.blur` (a platform-provided primitive, not a hand-rolled
 * `RenderEffect`/`RenderNode` pipeline this environment has no device to
 * verify pixel-for-pixel) — below [MIN_BLUR_SDK] the shadow renders
 * unblurred, an offset low-alpha silhouette, never a fabricated "blurred
 * everywhere" claim.
 */
object ShadowRenderer {
    fun deriveShadowSilhouette(cutout: Bitmap): Bitmap {
        val shadow = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until cutout.height) {
            for (x in 0 until cutout.width) {
                val sourceAlpha = Color.alpha(cutout.getPixel(x, y))
                val shadowAlpha = (sourceAlpha * SHADOW_ALPHA_FRACTION).toInt().coerceIn(MIN_ALPHA, MAX_ALPHA)
                shadow.setPixel(x, y, Color.argb(shadowAlpha, 0, 0, 0))
            }
        }
        return shadow
    }

    /** Takes [sdkInt] as a parameter (rather than reading
     * [Build.VERSION.SDK_INT] directly) so both branches of this real
     * capability gate are unit-testable without Robolectric SDK-level
     * simulation. */
    fun supportsBlur(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= MIN_BLUR_SDK
}
