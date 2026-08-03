package com.wardrobe.app.feature.closet.addwardrobe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.image.capture.GalleryImportSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Owns the "Choose from Gallery"/"Import Multiple Photos" half of the
 * Add-to-Wardrobe sheet — copying picked content [Uri]s to real files
 * (needed for lifecycle safety on a multi-photo import, unlike firing the
 * copy directly from a bare Composable callback) and enqueuing them into
 * the Room-backed import queue. "Take Photo" is a separate nav destination
 * ([com.wardrobe.app.feature.capture.navigation.GarmentCaptureRoute]) that
 * enqueues its own single file once captured.
 */
@HiltViewModel
class AddToWardrobeViewModel
    @Inject
    constructor(
        private val importQueueRepository: ImportQueueRepository,
        private val galleryImportSource: GalleryImportSource,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val mutableNavigateToQueue = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val navigateToQueue: SharedFlow<Unit> = mutableNavigateToQueue.asSharedFlow()

        fun onGalleryImagesPicked(uris: List<Uri>) {
            if (uris.isEmpty()) return
            viewModelScope.launch {
                val filePaths =
                    withContext(Dispatchers.IO) {
                        uris.map { uri ->
                            val destination = File.createTempFile("gallery_import_", ".jpg", context.cacheDir)
                            galleryImportSource.copyToTempFile(uri, destination)
                            destination.path
                        }
                    }
                importQueueRepository.enqueue(filePaths)
                mutableNavigateToQueue.emit(Unit)
            }
        }
    }
