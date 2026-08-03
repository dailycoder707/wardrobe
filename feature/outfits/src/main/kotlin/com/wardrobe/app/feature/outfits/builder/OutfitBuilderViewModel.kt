package com.wardrobe.app.feature.outfits.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.styling.analyzeColorHarmony
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnostics
import com.wardrobe.app.core.ui.debug.OutfitBuilderDiagnosticsSnapshot
import com.wardrobe.app.feature.outfits.common.toTileUiModel
import com.wardrobe.app.feature.outfits.navigation.OutfitBuilderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val MAX_HISTORY = 20

private val QUICK_ADD_ORDER =
    listOf(
        OutfitSlot.TOP,
        OutfitSlot.BOTTOM,
        OutfitSlot.OUTERWEAR,
        OutfitSlot.SHOES,
        OutfitSlot.BAG,
        OutfitSlot.JEWELRY,
        OutfitSlot.WATCH,
        OutfitSlot.ACCESSORIES,
    )

private data class GarmentContext(
    val garments: List<Garment>,
    val categories: List<Category>,
    val brands: List<Brand>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
)

private data class OutfitBuilderSession(
    val form: OutfitBuilderFormState = OutfitBuilderFormState(),
    val createdAt: Instant = Instant.now(),
    val source: OutfitSource = OutfitSource.USER_CREATED,
    val isArchived: Boolean = false,
    val photoUri: String? = null,
)

private data class MiscState(
    val undoCount: Int,
    val redoCount: Int,
    val isSaving: Boolean,
    val didSave: Boolean,
    val toast: String?,
)

@HiltViewModel
class OutfitBuilderViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val garmentRepository: GarmentRepository,
        private val outfitRepository: OutfitRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        occasionRepository: OccasionRepository,
        tagRepository: TagRepository,
    ) : ViewModel() {
        private val existingOutfitId = OutfitId(savedStateHandle.toRoute<OutfitBuilderRoute>().outfitId)

        private val slotsRaw = MutableStateFlow(restoreSlots(savedStateHandle))
        private val session = MutableStateFlow(restoreSession(savedStateHandle))
        private val undoStack = ArrayDeque<Map<OutfitSlot, GarmentId>>()
        private val redoStack = ArrayDeque<Map<OutfitSlot, GarmentId>>()
        private val undoRedoCounts = MutableStateFlow(0 to 0)
        private val savingState = MutableStateFlow(false)
        private val didSaveState = MutableStateFlow(false)
        private val toastMessage = MutableStateFlow<String?>(null)

        init {
            viewModelScope.launch { slotsRaw.collect { persistSlots(savedStateHandle, it) } }
            viewModelScope.launch { session.collect { persistSession(savedStateHandle, it) } }
            if (slotsRaw.value.isEmpty() && existingOutfitId.value != 0L) {
                viewModelScope.launch {
                    val existing = outfitRepository.getOutfit(existingOutfitId) ?: return@launch
                    slotsRaw.value = existing.garments.associate { OutfitSlot.fromIndex(it.layerSlot) to it.garmentId }
                    session.value = existing.toBuilderSession()
                }
            }
        }

        private val garmentContextFlow =
            combine(
                garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
            ) { garments, categories, brands ->
                GarmentContext(
                    garments = garments,
                    categories = categories,
                    brands = brands,
                    categoriesById = categories.associateBy { it.id },
                    brandsById = brands.associateBy { it.id },
                )
            }

        private val referenceExtrasFlow =
            combine(
                occasionRepository.observeAll(),
                tagRepository.observeAll(),
            ) { occasions, tags -> occasions to tags }

        private val miscFlow =
            combine(undoRedoCounts, savingState, didSaveState, toastMessage) { counts, saving, didSave, toast ->
                MiscState(counts.first, counts.second, saving, didSave, toast)
            }

        val uiState: StateFlow<OutfitBuilderUiState> =
            combine(garmentContextFlow, referenceExtrasFlow, slotsRaw, session, miscFlow) {
                garmentContext,
                referenceExtras,
                slots,
                currentSession,
                misc,
                ->
                buildUiState(garmentContext, referenceExtras, slots, currentSession, misc, existingOutfitId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = OutfitBuilderUiState(isLoading = true),
            )

        fun onPlaceGarment(
            slot: OutfitSlot,
            garmentId: GarmentId,
        ) {
            pushUndo(undoStack, redoStack, undoRedoCounts, slotsRaw.value)
            slotsRaw.update { current ->
                val updated = current.toMutableMap()
                updated[slot] = garmentId
                when (slot) {
                    OutfitSlot.DRESS -> {
                        updated.remove(OutfitSlot.TOP)
                        updated.remove(OutfitSlot.BOTTOM)
                    }

                    OutfitSlot.TOP, OutfitSlot.BOTTOM -> {
                        updated.remove(OutfitSlot.DRESS)
                    }

                    else -> {}
                }
                updated
            }
        }

        fun onRemoveGarment(slot: OutfitSlot) {
            pushUndo(undoStack, redoStack, undoRedoCounts, slotsRaw.value)
            slotsRaw.update { it - slot }
        }

        /** The mandatory non-drag equivalent for adding a garment (TalkBack,
         * switch access, or anyone who simply prefers tapping) — places
         * [garmentId] into the first empty slot in dressing order, or asks
         * the user to free one up if every slot is already filled. */
        fun onQuickAddGarment(garmentId: GarmentId) {
            val nextSlot = QUICK_ADD_ORDER.firstOrNull { it !in slotsRaw.value }
            if (nextSlot == null) {
                toastMessage.value = "Every slot is filled — tap one to replace it."
                return
            }
            onPlaceGarment(nextSlot, garmentId)
        }

        fun onClearOutfit() {
            if (slotsRaw.value.isEmpty()) return
            pushUndo(undoStack, redoStack, undoRedoCounts, slotsRaw.value)
            slotsRaw.value = emptyMap()
        }

        /** [undoRedoCounts] is deliberately set *before* [slotsRaw] in every
         * mutator below: `uiState`'s combine() emits once per source update,
         * and settling the stack-size count first means the one emission
         * that carries the new slot arrangement also already carries the
         * correct `canUndo`/`canRedo` — no separate transient frame where
         * the slots have moved but the counts haven't caught up yet. */
        fun onUndo() {
            val previous = undoStack.removeLastOrNull() ?: return
            redoStack.addLast(slotsRaw.value)
            undoRedoCounts.value = undoStack.size to redoStack.size
            slotsRaw.value = previous
        }

        fun onRedo() {
            val next = redoStack.removeLastOrNull() ?: return
            undoStack.addLast(slotsRaw.value)
            undoRedoCounts.value = undoStack.size to redoStack.size
            slotsRaw.value = next
        }

        fun onFormChange(form: OutfitBuilderFormState) {
            session.update { it.copy(form = form) }
        }

        fun onSave() {
            if (slotsRaw.value.isEmpty()) {
                toastMessage.value = "Add at least one item before saving."
                return
            }
            viewModelScope.launch {
                savingState.value = true
                val currentSession = session.value
                val outfit =
                    Outfit(
                        id = existingOutfitId,
                        name = currentSession.form.name.takeIf { it.isNotBlank() },
                        garments = slotsRaw.value.map { (slot, id) -> OutfitGarmentSlot(id, slot.slotIndex) },
                        occasionId = currentSession.form.occasionId,
                        source = currentSession.source,
                        isSaved = true,
                        isFavorite = currentSession.form.isFavorite,
                        isArchived = currentSession.isArchived,
                        notes = currentSession.form.notes.takeIf { it.isNotBlank() },
                        mood = currentSession.form.mood.takeIf { it.isNotBlank() },
                        seasons = currentSession.form.seasons,
                        dressCodes = currentSession.form.dressCodes,
                        tagIds = currentSession.form.tagIds.toList(),
                        photoUri = currentSession.photoUri,
                        createdAt = currentSession.createdAt,
                    )
                outfitRepository.saveOutfit(outfit)
                savingState.value = false
                didSaveState.value = true
                toastMessage.value = "Look saved"
            }
        }

        fun onToastShown() {
            toastMessage.value = null
        }

        /** [OutfitBuilderDiagnostics] is a process-wide singleton (so the
         * Developer Panel, in a different module, can read it) — without this,
         * "Builder open: Yes" would wrongly persist after the screen closes. */
        override fun onCleared() {
            OutfitBuilderDiagnostics.report(OutfitBuilderDiagnosticsSnapshot())
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5000L
        }
    }

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}

private fun Outfit.toBuilderSession(): OutfitBuilderSession =
    OutfitBuilderSession(
        form =
            OutfitBuilderFormState(
                name = name.orEmpty(),
                notes = notes.orEmpty(),
                mood = mood.orEmpty(),
                occasionId = occasionId,
                seasons = seasons,
                dressCodes = dressCodes,
                tagIds = tagIds.toSet(),
                isFavorite = isFavorite,
            ),
        createdAt = createdAt,
        source = source,
        isArchived = isArchived,
        photoUri = photoUri,
    )

/** [undoRedoCounts] is deliberately set *before* [slotsRaw][OutfitBuilderViewModel] in
 * every mutator that calls this: `uiState`'s combine() emits once per source update,
 * and settling the stack-size count first means the one emission that carries the new
 * slot arrangement also already carries the correct `canUndo`/`canRedo` — no separate
 * transient frame where the slots have moved but the counts haven't caught up yet. */
private fun pushUndo(
    undoStack: ArrayDeque<Map<OutfitSlot, GarmentId>>,
    redoStack: ArrayDeque<Map<OutfitSlot, GarmentId>>,
    undoRedoCounts: MutableStateFlow<Pair<Int, Int>>,
    currentSlots: Map<OutfitSlot, GarmentId>,
) {
    undoStack.addLast(currentSlots)
    redoStack.clear()
    if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    undoRedoCounts.value = undoStack.size to redoStack.size
}

private fun buildUiState(
    garmentContext: GarmentContext,
    referenceExtras: Pair<List<Occasion>, List<Tag>>,
    slots: Map<OutfitSlot, GarmentId>,
    currentSession: OutfitBuilderSession,
    misc: MiscState,
    existingOutfitId: OutfitId,
): OutfitBuilderUiState {
    val garmentsById = garmentContext.garments.associateBy { it.id }
    val slotTiles =
        slots
            .mapNotNull { (slot, garmentId) ->
                val garment = garmentsById[garmentId] ?: return@mapNotNull null
                slot to garment.toTileUiModel(garmentContext.categoriesById, garmentContext.brandsById)
            }.toMap()
    val closetTiles =
        garmentContext.garments.map { it.toTileUiModel(garmentContext.categoriesById, garmentContext.brandsById) }
    val harmonyHexValues =
        slots.values.mapNotNull { id ->
            garmentsById[id]
                ?.palette
                ?.maxByOrNull { it.weightPercent }
                ?.color
                ?.hexValue
        }

    OutfitBuilderDiagnostics.report(
        OutfitBuilderDiagnosticsSnapshot(
            isOpen = true,
            outfitId = existingOutfitId.value.takeIf { it != 0L },
            filledSlotCount = slots.size,
            totalSlotCount = OutfitSlot.entries.size,
            undoStackSize = misc.undoCount,
            redoStackSize = misc.redoCount,
        ),
    )

    return OutfitBuilderUiState(
        isLoading = false,
        slots = slotTiles,
        form = currentSession.form,
        closetGarments = closetTiles,
        reference =
            OutfitBuilderReferenceData(
                categories = garmentContext.categories,
                brands = garmentContext.brands,
                occasions = referenceExtras.first,
                tags = referenceExtras.second,
            ),
        canUndo = misc.undoCount > 0,
        canRedo = misc.redoCount > 0,
        colorHarmony = analyzeColorHarmony(harmonyHexValues),
        isSaving = misc.isSaving,
        didSave = misc.didSave,
        toastMessage = misc.toast,
    )
}

private fun persistSlots(
    handle: SavedStateHandle,
    slots: Map<OutfitSlot, GarmentId>,
) {
    handle["slot_indices"] = slots.keys.map { it.slotIndex }.toIntArray()
    handle["slot_garment_ids"] = slots.values.map { it.value }.toLongArray()
}

private fun restoreSlots(handle: SavedStateHandle): Map<OutfitSlot, GarmentId> {
    val indices = handle.get<IntArray>("slot_indices")
    val garmentIds = handle.get<LongArray>("slot_garment_ids")
    return if (indices == null || garmentIds == null) {
        emptyMap()
    } else {
        indices.indices.associate { i -> OutfitSlot.fromIndex(indices[i]) to GarmentId(garmentIds[i]) }
    }
}

private fun persistSession(
    handle: SavedStateHandle,
    session: OutfitBuilderSession,
) {
    handle["form_name"] = session.form.name
    handle["form_notes"] = session.form.notes
    handle["form_mood"] = session.form.mood
    handle["form_occasion_id"] = session.form.occasionId?.value ?: 0L
    handle["form_seasons"] =
        session.form.seasons
            .map { it.name }
            .toTypedArray()
    handle["form_dress_codes"] =
        session.form.dressCodes
            .map { it.name }
            .toTypedArray()
    handle["form_tag_ids"] =
        session.form.tagIds
            .map { it.value }
            .toLongArray()
    handle["form_is_favorite"] = session.form.isFavorite
    handle["meta_created_at"] = session.createdAt.toEpochMilli()
    handle["meta_source"] = session.source.name
    handle["meta_is_archived"] = session.isArchived
    handle["meta_photo_uri"] = session.photoUri
}

private fun restoreSession(handle: SavedStateHandle): OutfitBuilderSession {
    val name = handle.get<String>("form_name") ?: return OutfitBuilderSession()
    val form =
        OutfitBuilderFormState(
            name = name,
            notes = handle.get<String>("form_notes").orEmpty(),
            mood = handle.get<String>("form_mood").orEmpty(),
            occasionId = handle.get<Long>("form_occasion_id")?.takeIf { it != 0L }?.let(::OccasionId),
            seasons = handle.get<Array<String>>("form_seasons")?.map(Season::valueOf)?.toSet() ?: emptySet(),
            dressCodes = handle.get<Array<String>>("form_dress_codes")?.map(DressCode::valueOf)?.toSet() ?: emptySet(),
            tagIds = handle.get<LongArray>("form_tag_ids")?.map(::TagId)?.toSet() ?: emptySet(),
            isFavorite = handle.get<Boolean>("form_is_favorite") ?: false,
        )
    return OutfitBuilderSession(
        form = form,
        createdAt = handle.get<Long>("meta_created_at")?.let(Instant::ofEpochMilli) ?: Instant.now(),
        source = handle.get<String>("meta_source")?.let(OutfitSource::valueOf) ?: OutfitSource.USER_CREATED,
        isArchived = handle.get<Boolean>("meta_is_archived") ?: false,
        photoUri = handle.get<String>("meta_photo_uri"),
    )
}
