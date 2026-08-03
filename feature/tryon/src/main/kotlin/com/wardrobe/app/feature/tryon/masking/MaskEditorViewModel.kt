package com.wardrobe.app.feature.tryon.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.domain.repository.GarmentMaskRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.tryon.GarmentMask
import com.wardrobe.app.core.tryon.masking.GarmentMaskEditor
import com.wardrobe.app.feature.tryon.navigation.MaskEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

data class MaskEditorUiState(
    val isLoading: Boolean = true,
    val workingBitmap: Bitmap? = null,
)

/**
 * [originalBitmap] is kept immutable alongside the mutable working copy in
 * [uiState] specifically so [GarmentMaskEditor.restore] always has the
 * garment's *true* original alpha to restore to — never the working copy's
 * own already-edited alpha, which would make repeated erase/restore passes
 * lossy. The working bitmap starts from any existing [GarmentMask] (so
 * reopening this screen resumes prior edits) or, absent one, from the
 * garment's own cutout unmodified.
 */
@HiltViewModel
class MaskEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        @ApplicationContext private val context: Context,
        private val bodyProfileRepository: BodyProfileRepository,
        private val garmentRepository: GarmentRepository,
        private val garmentMaskRepository: GarmentMaskRepository,
    ) : ViewModel() {
        private val garmentId = GarmentId(savedStateHandle.toRoute<MaskEditorRoute>().garmentId)
        private var bodyProfileId: BodyProfileId? = null
        private var originalBitmap: Bitmap? = null
        private val mutableUiState = MutableStateFlow(MaskEditorUiState())
        val uiState: StateFlow<MaskEditorUiState> = mutableUiState.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val profile = bodyProfileRepository.observeBodyProfile().first() ?: return
            bodyProfileId = profile.id
            val cutoutPath = garmentRepository.getGarment(garmentId)?.let(::cutoutPathFor)
            val cutout = cutoutPath?.let(BitmapFactory::decodeFile)
            if (cutout == null) {
                return
            }
            originalBitmap = cutout
            val existingMaskPath = garmentMaskRepository.observeGarmentMask(profile.id, garmentId).first()?.maskFilePath
            val working =
                existingMaskPath?.let(BitmapFactory::decodeFile) ?: cutout.copy(Bitmap.Config.ARGB_8888, true)
            mutableUiState.value = MaskEditorUiState(isLoading = false, workingBitmap = working)
        }

        private fun cutoutPathFor(garment: com.wardrobe.app.core.model.garment.Garment): String? =
            garment.images.firstOrNull { it.type == ImageType.CUTOUT }?.filePath
                ?: garment.images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath

        fun erase(
            x: Int,
            y: Int,
        ) = applyBrush { bitmap -> GarmentMaskEditor.erase(bitmap, x, y) }

        fun restore(
            x: Int,
            y: Int,
        ) {
            val original = originalBitmap ?: return
            applyBrush { bitmap -> GarmentMaskEditor.restore(bitmap, original, x, y) }
        }

        private fun applyBrush(operation: (Bitmap) -> Bitmap) {
            val current = mutableUiState.value.workingBitmap ?: return
            mutableUiState.value = mutableUiState.value.copy(workingBitmap = operation(current))
        }

        fun save() {
            val profileId = bodyProfileId ?: return
            val bitmap = mutableUiState.value.workingBitmap ?: return
            viewModelScope.launch {
                val file = File.createTempFile("mask_", ".png", context.cacheDir)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
                garmentMaskRepository.saveGarmentMask(
                    GarmentMask(
                        bodyProfileId = profileId,
                        garmentId = garmentId,
                        maskFilePath = file.path,
                        updatedAt = Instant.now(),
                    ),
                )
            }
        }
    }

private const val PNG_QUALITY = 100
