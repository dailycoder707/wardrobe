package com.wardrobe.app.core.image.capture

import android.content.Context
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * The Photo Picker (`ActivityResultContracts.PickVisualMedia`) needs no
 * runtime permission on API 33+ (Phase 1 Section 15); the `READ_EXTERNAL_
 * STORAGE` fallback below API 33 is already declared in the manifest
 * (Phase 2). This class only owns copying the picked content [Uri] into a
 * plain [File] — the rest of the pipeline works on files, not URIs.
 */
class GalleryImportSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun pickVisualMediaRequest(): PickVisualMediaRequest =
            PickVisualMediaRequest
                .Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()

        fun copyToTempFile(
            uri: Uri,
            destination: File,
        ) {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open $uri")
            input.use { stream -> destination.outputStream().use { output -> stream.copyTo(output) } }
        }
    }
