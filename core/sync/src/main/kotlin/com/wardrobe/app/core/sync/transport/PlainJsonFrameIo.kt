package com.wardrobe.app.core.sync.transport

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Length-prefixed, unencrypted JSON — used only for the two exchanges that
 * necessarily happen *before* any shared secret exists yet: pairing itself
 * ([com.wardrobe.app.core.sync.pairing.PairingExchange]) and the identity-
 * signed handshake ([SyncHandshake]) that derives the session key every
 * later message is actually encrypted with. */
internal object PlainJsonFrameIo {
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> write(
        outputStream: OutputStream,
        serializer: KSerializer<T>,
        body: T,
    ) {
        val bytes = json.encodeToString(serializer, body).toByteArray(Charsets.UTF_8)
        DataOutputStream(outputStream).apply {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    fun <T> read(
        inputStream: InputStream,
        serializer: KSerializer<T>,
    ): T {
        val dataInput = DataInputStream(inputStream)
        val bytes = ByteArray(dataInput.readInt()).also { dataInput.readFully(it) }
        return json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    }
}
