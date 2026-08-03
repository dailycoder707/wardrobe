package com.wardrobe.app.core.image.hashing

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 of a file's bytes, streamed rather than loaded whole — pure
 * `java.io`/`java.security`, no Android dependency, so it's testable without
 * Robolectric. Used both for `image_metadata.checksum` (Backup/Restore's
 * integrity check, ADR-007) and as the mechanism (not the policy — see
 * phase-5b-image-pipeline.md's "File naming" section) for detecting duplicate
 * imports.
 */
object ImageHasher {
    private const val BUFFER_SIZE = 8192

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
