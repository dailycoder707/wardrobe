package com.wardrobe.app.core.image.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageHasherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `same content produces the same hash`() {
        val fileA = tempFolder.newFile("a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val fileB = tempFolder.newFile("b.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        assertEquals(ImageHasher.sha256(fileA), ImageHasher.sha256(fileB))
    }

    @Test
    fun `different content produces a different hash`() {
        val fileA = tempFolder.newFile("a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val fileB = tempFolder.newFile("b.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 6)) }

        assertNotEquals(ImageHasher.sha256(fileA), ImageHasher.sha256(fileB))
    }

    @Test
    fun `hash is a 64-character lowercase hex string`() {
        val file = tempFolder.newFile("a.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }

        val hash = ImageHasher.sha256(file)

        assertEquals(64, hash.length)
        assertEquals(hash, hash.lowercase())
    }
}
