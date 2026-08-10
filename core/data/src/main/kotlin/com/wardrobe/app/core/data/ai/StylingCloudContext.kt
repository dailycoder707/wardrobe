package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.model.styling.SuggestionContext
import java.security.MessageDigest

private const val FINGERPRINT_BITMAP_SIDE = 8
private const val BYTES_PER_PIXEL = 4
private const val CHANNEL_MASK = 0xFF
private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/**
 * [CloudStylingEngine]'s cache-key vehicle when the user hasn't attached an
 * inspiration image. The [com.wardrobe.app.core.ai.gateway.AiGateway]'s
 * cache key is always `sha256(image):capability:provider:model:promptVersion`
 * (ADR-012 §4) — there is no separate "extra fields" hook for a capability
 * to contribute non-image cache-key material. Rather than adding one (which
 * would touch the Gateway's cache architecture for every capability, not
 * just this one), Styling encodes exactly the fields M12's cache-key spec
 * calls for — wardrobe state, weather, occasion — into a small deterministic
 * bitmap and sends *that* as the "image." Identical wardrobe/weather/
 * occasion always hash to the identical bitmap, so the Gateway's existing,
 * unmodified cache lookup already behaves exactly like a
 * `(wardrobeHash, weatherHash, occasion, provider, model, promptVersion)`
 * key without a single line of Gateway code changing.
 */
internal fun stylingContextFingerprintBitmap(
    input: EngineInput,
    context: SuggestionContext,
): Bitmap {
    val seed = "${wardrobeHash(input)}::${weatherHash(input)}::${occasionHash(context)}"
    return fingerprintBitmapOf(seed)
}

private fun wardrobeHash(input: EngineInput): String =
    input.candidateGarments
        .sortedBy { it.id.value }
        .joinToString("|") { "${it.id.value}:${it.categoryId.value}:${it.primaryColorId?.value}:${it.updatedAt}" }

private fun weatherHash(input: EngineInput): String =
    input.weather?.let { "${it.apparentTempHighC}:${it.apparentTempLowC}:${it.condition}" } ?: "no-weather"

private fun occasionHash(context: SuggestionContext): String = context.occasionId?.value?.toString() ?: "no-occasion"

private fun fingerprintBitmapOf(seed: String): Bitmap {
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
    val bitmap = Bitmap.createBitmap(FINGERPRINT_BITMAP_SIDE, FINGERPRINT_BITMAP_SIDE, Bitmap.Config.ARGB_8888)
    for (y in 0 until FINGERPRINT_BITMAP_SIDE) {
        for (x in 0 until FINGERPRINT_BITMAP_SIDE) {
            val i = ((y * FINGERPRINT_BITMAP_SIDE + x) * BYTES_PER_PIXEL) % digest.size
            val r = digest[i].toInt() and CHANNEL_MASK
            val g = digest[(i + 1) % digest.size].toInt() and CHANNEL_MASK
            val b = digest[(i + 2) % digest.size].toInt() and CHANNEL_MASK
            bitmap.setPixel(x, y, (OPAQUE_ALPHA shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b)
        }
    }
    return bitmap
}
