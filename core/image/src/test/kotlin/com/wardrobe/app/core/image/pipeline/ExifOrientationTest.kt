package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * `readOrientation` is pointed at arbitrary user-supplied photos (camera
 * capture and gallery import), so it has to read a real EXIF orientation tag
 * off a real file — not merely avoid crashing. These tests write a genuine
 * JPEG, tag it, and read the tag back, which is the behaviour the whole
 * pipeline's "store `original.jpg` already-upright" guarantee rests on.
 */
@RunWith(RobolectricTestRunner::class)
class ExifOrientationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun jpegTaggedWith(orientation: Int): File {
        val file = temporaryFolder.newFile("photo.jpg")
        file.outputStream().use { out ->
            Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    @Test
    fun `reads a real ORIENTATION_ROTATE_90 tag off a real JPEG`() {
        val file = jpegTaggedWith(ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, ExifOrientation.readOrientation(file))
    }

    @Test
    fun `reads a real ORIENTATION_ROTATE_270 tag off a real JPEG`() {
        val file = jpegTaggedWith(ExifInterface.ORIENTATION_ROTATE_270)

        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, ExifOrientation.readOrientation(file))
    }

    /** A file with no EXIF segment is not corrupted — it simply has nothing to
     * correct, and must read as NORMAL rather than failing the pipeline. */
    @Test
    fun `an untagged file reads as ORIENTATION_NORMAL`() {
        val file = temporaryFolder.newFile("plain.jpg")
        file.outputStream().use { out ->
            Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifOrientation.readOrientation(file))
    }

    @Test
    fun `a file that does not exist reads as ORIENTATION_NORMAL rather than throwing`() {
        val missing = File(temporaryFolder.root, "nope.jpg")

        assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifOrientation.readOrientation(missing))
    }

    @Test
    fun `ORIENTATION_ROTATE_90 swaps the bitmap's width and height`() {
        val source = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888)

        val corrected = ExifOrientation.correct(source, ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(8, corrected.width)
        assertEquals(4, corrected.height)
    }

    @Test
    fun `ORIENTATION_NORMAL returns the bitmap untouched`() {
        val source = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888)

        assertEquals(source, ExifOrientation.correct(source, ExifInterface.ORIENTATION_NORMAL))
    }
}
