package com.wardrobe.app.core.sync.transport

import com.wardrobe.app.core.sync.crypto.SessionCrypto
import com.wardrobe.app.core.sync.protocol.SyncFrame
import com.wardrobe.app.core.sync.protocol.SyncMessageKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class EncryptedFrameTransportTest {
    private val sharedKey =
        SessionCrypto.deriveSessionKey(
            SessionCrypto.generateEphemeralKeyPair(),
            SessionCrypto.generateEphemeralKeyPair().public,
        )

    @Test
    fun `a sent frame decodes back to the same frame on the receiving side`() {
        val wire = ByteArrayOutputStream()
        val sender = EncryptedFrameTransport(ByteArrayInputStream(ByteArray(0)), wire, sharedKey)
        val frame = SyncFrame(SyncMessageKind.CHANGE_BATCH, """{"changes":[],"cursor":5}""")

        sender.send(frame)
        val receiver =
            EncryptedFrameTransport(ByteArrayInputStream(wire.toByteArray()), ByteArrayOutputStream(), sharedKey)
        val received = receiver.receive()

        assertEquals(frame, received)
    }

    @Test
    fun `receiving a frame stamped with the wrong sequence number fails loudly`() {
        // Hand-built rather than sent through a real sender — this simulates
        // a replayed or reordered frame arriving with a sequence number the
        // receiver (which always starts expecting 0) was never told to skip
        // ahead to.
        val wire = ByteArrayOutputStream()
        val output = DataOutputStream(wire)
        val iv = SessionCrypto.randomIv()
        val plaintext =
            Json.encodeToString(SyncFrame.serializer(), SyncFrame(SyncMessageKind.DONE, "")).toByteArray(Charsets.UTF_8)
        val wrongSequence = 5L
        val ciphertext = SessionCrypto.encrypt(sharedKey, iv, plaintext, wrongSequence.toString().toByteArray())
        output.writeLong(wrongSequence)
        output.writeInt(iv.size)
        output.write(iv)
        output.writeInt(ciphertext.size)
        output.write(ciphertext)

        val receiver =
            EncryptedFrameTransport(ByteArrayInputStream(wire.toByteArray()), ByteArrayOutputStream(), sharedKey)

        assertThrows(IllegalStateException::class.java) { receiver.receive() }
    }

    @Test
    fun `bytes sent and received are tracked identically for diagnostics`() {
        val wire = ByteArrayOutputStream()
        val sender = EncryptedFrameTransport(ByteArrayInputStream(ByteArray(0)), wire, sharedKey)
        sender.send(SyncFrame(SyncMessageKind.DONE, ""))

        assert(sender.totalBytesSent > 0)

        val receiver =
            EncryptedFrameTransport(ByteArrayInputStream(wire.toByteArray()), ByteArrayOutputStream(), sharedKey)
        receiver.receive()

        assertEquals(sender.totalBytesSent, receiver.totalBytesReceived)
    }
}
