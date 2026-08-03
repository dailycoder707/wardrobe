package com.wardrobe.app.core.sync.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.json.Json

/**
 * QR generate/scan (Phase 8) — plain ZXing (`com.google.zxing:core`), no
 * Google Play Services / ML Kit dependency (see `core:sync`'s own
 * `build.gradle.kts` comment): a QR is encode/decode of a small text
 * payload, not a task that needs an ML model.
 */
object PairingQrCodec {
    private const val QR_SIZE_PX = 512
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: PairingOfferPayload): Bitmap {
        val text = json.encodeToString(PairingOfferPayload.serializer(), payload)
        val matrix: BitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
        return matrix.toBitmap()
    }

    /** Returns `null` for anything that isn't a recognizable QR frame (a
     * blurry preview frame, an unrelated QR code) rather than throwing —
     * the camera preview calls this on every analyzed frame and a miss is
     * the overwhelmingly common case, not an error. */
    fun decode(luminanceSource: LuminanceSource): PairingOfferPayload? {
        val bitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
        return try {
            val text = MultiFormatReader().decode(bitmap).text
            json.decodeFromString(PairingOfferPayload.serializer(), text)
        } catch (_: NotFoundException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }
    }

    fun decode(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): PairingOfferPayload? = decode(RGBLuminanceSource(width, height, pixels))

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
