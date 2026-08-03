package com.wardrobe.app.core.sync.pairing

import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingQrCodecTest {
    private val payload =
        PairingOfferPayload(
            deviceId = "tablet-fingerprint",
            displayName = "Kitchen Tablet",
            identityPublicKeyBase64 = "not-a-real-key-just-test-text",
            pairingToken = "one-time-token-123",
            hostAddress = "192.168.1.42",
            hostPort = 54321,
        )

    @Test
    fun `encoding then decoding a pairing offer round-trips every field`() {
        val bitmap = PairingQrCodec.encode(payload)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = PairingQrCodec.decode(pixels, bitmap.width, bitmap.height)

        assertEquals(payload, decoded)
    }

    @Test
    fun `decoding a frame with no QR code returns null instead of throwing`() {
        val width = 50
        val height = 50
        val blankPixels = IntArray(width * height) { 0xFFFFFF }
        val source = RGBLuminanceSource(width, height, blankPixels)

        assertNull(PairingQrCodec.decode(source))
    }
}
