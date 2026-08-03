package com.wardrobe.app.core.image.validation

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ImageValidatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a real jpeg of adequate size is valid`() {
        val file = jpegFile("real.jpg", 300)
        assertEquals(ValidationResult.Valid, ImageValidator.validate(file))
    }

    @Test
    fun `a missing file is invalid`() {
        val file = File(tempFolder.root, "missing.jpg")
        assertTrue(ImageValidator.validate(file) is ValidationResult.Invalid)
    }

    @Test
    fun `an empty file is invalid`() {
        val file = tempFolder.newFile("empty.jpg")
        assertTrue(ImageValidator.validate(file) is ValidationResult.Invalid)
    }

    @Test
    fun `garbage bytes are invalid`() {
        val file = tempFolder.newFile("garbage.jpg").apply { writeBytes(ByteArray(50) { it.toByte() }) }
        assertTrue(ImageValidator.validate(file) is ValidationResult.Invalid)
    }

    @Test
    fun `an image smaller than the minimum dimension is invalid`() {
        val file = jpegFile("tiny.jpg", 50)
        assertTrue(ImageValidator.validate(file) is ValidationResult.Invalid)
    }

    private fun jpegFile(
        name: String,
        size: Int,
    ): File {
        val file = tempFolder.newFile(name)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }
}
