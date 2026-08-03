package com.wardrobe.app.core.sync.transport

import com.wardrobe.app.core.sync.crypto.SessionCrypto
import com.wardrobe.app.core.sync.protocol.SyncFrame
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.spec.SecretKeySpec

/**
 * Length-prefixed, per-frame-encrypted messages over a plain socket stream
 * (Phase 8). Every frame's associated data is its own sequence number, so a
 * captured frame replayed later — or two frames delivered out of order —
 * fails AES-GCM's authentication tag check instead of silently decrypting
 * (Constitution: "protect against replay attacks"). Pure `java.io`/
 * `javax.crypto` — no Android dependency, so this is testable over a plain
 * loopback [java.net.Socket] pair on the JVM.
 */
class EncryptedFrameTransport(
    inputStream: InputStream,
    outputStream: OutputStream,
    private val sessionKey: SecretKeySpec,
) {
    private val input = DataInputStream(inputStream)
    private val output = DataOutputStream(outputStream)
    private var sendSequence = 0L
    private var receiveSequence = 0L
    private val json = Json { ignoreUnknownKeys = true }

    /** Cumulative ciphertext bytes, framing overhead included — read by the
     * Developer Panel's "Bytes transferred" diagnostic and Sync History. */
    var totalBytesSent: Long = 0
        private set
    var totalBytesReceived: Long = 0
        private set

    @Synchronized
    fun send(frame: SyncFrame) {
        val plaintext = json.encodeToString(SyncFrame.serializer(), frame).toByteArray(Charsets.UTF_8)
        val iv = SessionCrypto.randomIv()
        val ciphertext = SessionCrypto.encrypt(sessionKey, iv, plaintext, aad(sendSequence))
        output.writeLong(sendSequence)
        output.writeInt(iv.size)
        output.write(iv)
        output.writeInt(ciphertext.size)
        output.write(ciphertext)
        output.flush()
        totalBytesSent += (java.lang.Long.BYTES + Integer.BYTES + iv.size + Integer.BYTES + ciphertext.size).toLong()
        sendSequence++
    }

    @Synchronized
    fun receive(): SyncFrame {
        val sequence = input.readLong()
        check(sequence == receiveSequence) {
            "Expected frame $receiveSequence but received $sequence — possible replay or reordering"
        }
        val iv = ByteArray(input.readInt()).also { input.readFully(it) }
        val ciphertext = ByteArray(input.readInt()).also { input.readFully(it) }
        val plaintext = SessionCrypto.decrypt(sessionKey, iv, ciphertext, aad(sequence))
        val frameOverhead = java.lang.Long.BYTES + Integer.BYTES + iv.size + Integer.BYTES + ciphertext.size
        totalBytesReceived += frameOverhead.toLong()
        receiveSequence++
        return json.decodeFromString(SyncFrame.serializer(), plaintext.toString(Charsets.UTF_8))
    }

    private fun aad(sequence: Long): ByteArray = sequence.toString().toByteArray(Charsets.UTF_8)
}
