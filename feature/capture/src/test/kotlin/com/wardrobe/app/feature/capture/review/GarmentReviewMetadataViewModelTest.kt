package com.wardrobe.app.feature.capture.review

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.QualityCheck
import com.wardrobe.app.core.model.garment.QualityCheckName
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.QualityVerdict
import com.wardrobe.app.core.model.garment.ReconstructionOutcome
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.StagedImage
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.feature.capture.fakes.FakeBrandRepository
import com.wardrobe.app.feature.capture.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.capture.fakes.FakeColorRepository
import com.wardrobe.app.feature.capture.fakes.FakeFabricRepository
import com.wardrobe.app.feature.capture.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.capture.fakes.FakeImageRepository
import com.wardrobe.app.feature.capture.fakes.FakeImportQueueRepository
import com.wardrobe.app.feature.capture.fakes.FakeMaterialRepository
import com.wardrobe.app.feature.capture.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.capture.fakes.FakeTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GarmentReviewMetadataViewModelTest {
    private lateinit var importQueueRepository: FakeImportQueueRepository
    private lateinit var imageRepository: FakeImageRepository
    private lateinit var garmentRepository: FakeGarmentRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var colorRepository: FakeColorRepository

    private val topsCategoryId = CategoryId(1)
    private val subcategoryId = CategoryId(2)
    private val blueColorId = ColorId(1)
    private val cottonMaterialId = MaterialId(1)
    private val jerseyFabricId = FabricId(1)
    private val casualOccasionId = OccasionId(1)
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        importQueueRepository = FakeImportQueueRepository()
        imageRepository = FakeImageRepository()
        garmentRepository = FakeGarmentRepository()
        categoryRepository =
            FakeCategoryRepository(
                listOf(
                    Category(topsCategoryId, "Tops", null, CategoryLevel.TOP),
                    Category(subcategoryId, "T-Shirts", topsCategoryId, CategoryLevel.SUB),
                ),
            )
        colorRepository = FakeColorRepository(listOf(Color(blueColorId, "Blue", "#0000FF")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedReadyItem(
        stagingId: String = "staging-1",
        checksum: String = "hash-1",
        metadataSuggestions: List<MetadataSuggestion> = emptyList(),
        qualityChecks: List<QualityCheck> = emptyList(),
    ): Long {
        val item = importQueueRepository.enqueue(listOf("/a.jpg")).single()
        importQueueRepository.updateItem(
            item.copy(status = ImportQueueItemStatus.READY_FOR_REVIEW, stagingId = stagingId),
        )
        imageRepository.stagedImages[stagingId] =
            StagedImage(
                stagingId = stagingId,
                variants =
                    listOf(
                        ImageVariant(ImageType.ORIGINAL, "/orig.jpg", 500, 500, 100L, "jpg", checksum),
                        ImageVariant(ImageType.CUTOUT, "/cutout.png", 500, 500, 100L, "png", "cutout-hash"),
                    ),
                qualityReport = QualityReport(qualityChecks),
                backgroundRemovalStatus = BackgroundRemovalStatus.SUCCEEDED,
                cutoutConfidence = 0.9f,
                reconstructionOutcome = ReconstructionOutcome.NOT_ATTEMPTED,
                occlusionSeverity = null,
                metadataSuggestions = metadataSuggestions,
            )
        return item.id
    }

    private fun viewModel(
        queueItemId: Long,
        materialRepository: FakeMaterialRepository = FakeMaterialRepository(),
        fabricRepository: FakeFabricRepository = FakeFabricRepository(),
        occasionRepository: FakeOccasionRepository = FakeOccasionRepository(),
    ) = GarmentReviewMetadataViewModel(
        savedStateHandle = SavedStateHandle(mapOf("queueItemId" to queueItemId)),
        importQueueRepository = importQueueRepository,
        imageRepository = imageRepository,
        garmentRepository = garmentRepository,
        taxonomy =
            ReviewTaxonomyRepositories(
                categoryRepository = categoryRepository,
                brandRepository = FakeBrandRepository(),
                colorRepository = colorRepository,
                materialRepository = materialRepository,
                tagRepository = FakeTagRepository(),
                fabricRepository = fabricRepository,
                occasionRepository = occasionRepository,
            ),
        context = ApplicationProvider.getApplicationContext(),
    )

    private fun suggestion(
        field: MetadataField,
        value: String,
        confidence: Float?,
    ) = MetadataSuggestion(
        field,
        value,
        confidence,
        AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
    )

    @Test
    fun `loads the cutout preview and detects an exact-file duplicate by checksum`() =
        runTest {
            val garmentId = garmentRepository.saveGarment(baseGarment(name = "Existing Shirt"))
            imageRepository.checksumOwner = garmentId
            val queueItemId = seedReadyItem(checksum = "hash-1")

            val vm = viewModel(queueItemId)

            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("/cutout.png", state.previewImagePath)
                assertEquals("Existing Shirt", state.checksumDuplicateGarmentName)
            }
        }

    @Test
    fun `changing category and color surfaces a metadata duplicate banner`() =
        runTest {
            val existing = baseGarment(name = "Similar Item", color = ColorId(9))
            garmentRepository.saveGarment(existing)
            garmentRepository.duplicatesToReturn = listOf(existing)
            val queueItemId = seedReadyItem()

            val vm = viewModel(queueItemId)
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onFormChange(GarmentMetadataFormState(categoryId = topsCategoryId, primaryColorId = ColorId(9)))
                val state = awaitMatching { it.potentialDuplicates.isNotEmpty() }
                assertEquals(listOf("Similar Item"), state.potentialDuplicates.map { it.name })
            }
        }

    @Test
    fun `onSave requires a category and does nothing without one`() =
        runTest {
            val queueItemId = seedReadyItem()
            val vm = viewModel(queueItemId)
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onSave()
                assertEquals(0, garmentRepository.savedGarments.size)
            }
        }

    @Test
    fun `onSave commits the staged image and marks the queue item COMPLETED`() =
        runTest {
            val queueItemId = seedReadyItem(stagingId = "staging-save")
            val vm = viewModel(queueItemId)
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onFormChange(GarmentMetadataFormState(categoryId = topsCategoryId, name = "New Shirt"))
                vm.onSave()
                val state = awaitMatching { it.didSave }
                assertTrue(state.didSave)
            }
            assertEquals(1, garmentRepository.savedGarments.size)
            assertTrue(garmentRepository.savedGarments.single().isReviewed)
            assertEquals(listOf("staging-save"), imageRepository.committedStagingIds)
            val completedItem = importQueueRepository.currentItems().single()
            assertEquals(ImportQueueItemStatus.COMPLETED, completedItem.status)
            assertEquals(garmentRepository.savedGarments.single().id, completedItem.savedGarmentId)
        }

    @Test
    fun `onSaveAsDraft saves with isReviewed false`() =
        runTest {
            val queueItemId = seedReadyItem()
            val vm = viewModel(queueItemId)
            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onFormChange(GarmentMetadataFormState(categoryId = topsCategoryId))
                vm.onSaveAsDraft()
                awaitMatching { it.didSave }
            }
            assertEquals(false, garmentRepository.savedGarments.single().isReviewed)
        }

    @Test
    fun `a lost staged image resets the queue item to PENDING and signals a restage`() =
        runTest {
            val item = importQueueRepository.enqueue(listOf("/a.jpg")).single()
            importQueueRepository.updateItem(
                item.copy(status = ImportQueueItemStatus.READY_FOR_REVIEW, stagingId = "staging-missing"),
            )
            // Deliberately not registering a StagedImage for "staging-missing".

            val vm = viewModel(item.id)
            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.needsRestage)
                assertNull(state.previewImagePath)
            }
            val resetItem = importQueueRepository.currentItems().single()
            assertEquals(ImportQueueItemStatus.PENDING, resetItem.status)
        }

    @Test
    fun `a HIGH-confidence suggestion is auto-selected into the form`() =
        runTest {
            val queueItemId =
                seedReadyItem(
                    metadataSuggestions =
                        listOf(
                            suggestion(MetadataField.CATEGORY, "Tops", 0.95f),
                            suggestion(MetadataField.PRIMARY_COLOR, "Blue", 0.9f),
                        ),
                )
            val vm = viewModel(queueItemId)

            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(topsCategoryId, state.form.categoryId)
                assertEquals(blueColorId, state.form.primaryColorId)
            }
        }

    @Test
    fun `a MEDIUM-confidence suggestion is not auto-selected but is surfaced for the chip`() =
        runTest {
            val queueItemId =
                seedReadyItem(metadataSuggestions = listOf(suggestion(MetadataField.CATEGORY, "Tops", 0.6f)))
            val vm = viewModel(queueItemId)

            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertNull(state.form.categoryId)
                assertEquals(1, state.metadataSuggestions.size)
            }
        }

    @Test
    fun `onToggleSuggestion applies a MEDIUM suggestion, and tapping it again clears it`() =
        runTest {
            val queueItemId =
                seedReadyItem(metadataSuggestions = listOf(suggestion(MetadataField.CATEGORY, "Tops", 0.6f)))
            val vm = viewModel(queueItemId)

            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onToggleSuggestion(MetadataField.CATEGORY, "Tops")
                val applied = awaitMatching { it.form.categoryId != null }
                assertEquals(topsCategoryId, applied.form.categoryId)

                vm.onToggleSuggestion(MetadataField.CATEGORY, "Tops")
                val cleared = awaitMatching { it.form.categoryId == null }
                assertNull(cleared.form.categoryId)
            }
        }

    @Test
    fun `quality warnings surface every non-PASS check but never a passing one`() =
        runTest {
            val queueItemId =
                seedReadyItem(
                    qualityChecks =
                        listOf(
                            QualityCheck(QualityCheckName.SHARPNESS, QualityVerdict.WARNING, "Slightly blurry"),
                            QualityCheck(QualityCheckName.RESOLUTION, QualityVerdict.PASS, "Looks good"),
                        ),
                )
            val vm = viewModel(queueItemId)

            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(1, state.qualityWarnings.size)
                assertEquals(QualityCheckName.SHARPNESS, state.qualityWarnings.single().name)
            }
        }

    @Test
    fun `onRetryStage calls the repository and refreshes the form from the new suggestions`() =
        runTest {
            val queueItemId = seedReadyItem(stagingId = "staging-retry")
            imageRepository.onRetryStage = { _, staged ->
                staged.copy(metadataSuggestions = listOf(suggestion(MetadataField.CATEGORY, "Tops", 0.97f)))
            }
            val vm = viewModel(queueItemId)

            vm.uiState.test {
                awaitMatching { !it.isLoading }
                vm.onRetryStage(ImageRetryStage.METADATA)
                val state = awaitMatching { it.form.categoryId != null }
                assertEquals(topsCategoryId, state.form.categoryId)
            }
            assertEquals(listOf("staging-retry" to ImageRetryStage.METADATA), imageRepository.retryStageCalls)
        }

    /** Every `AutoSaveEligibility` required field at HIGH confidence,
     * resolving against real reference rows seeded in [autoSaveReadyItem] —
     * mirrors what a genuinely confident cloud metadata response looks
     * like. */
    private fun fullRequiredConfidenceSuggestions(): List<MetadataSuggestion> =
        listOf(
            suggestion(MetadataField.CATEGORY, "Tops", 0.95f),
            suggestion(MetadataField.SUBCATEGORY, "T-Shirts", 0.93f),
            suggestion(MetadataField.PRIMARY_COLOR, "Blue", 0.9f),
            suggestion(MetadataField.PATTERN, "Solid", 0.92f),
            suggestion(MetadataField.MATERIAL, "Cotton", 0.9f),
            suggestion(MetadataField.FABRIC, "Jersey", 0.9f),
            suggestion(MetadataField.FIT, "REGULAR", 0.9f),
            suggestion(MetadataField.DRESS_CODE, "CASUAL", 0.9f),
            suggestion(MetadataField.SEASON, "SUMMER", 0.9f),
            suggestion(MetadataField.OCCASION, "Casual", 0.9f),
        )

    private suspend fun autoSaveReadyItem(suggestions: List<MetadataSuggestion>): Long =
        seedReadyItem(stagingId = "staging-autosave", metadataSuggestions = suggestions)

    private fun viewModelWithFullReferenceData(queueItemId: Long) =
        viewModel(
            queueItemId,
            materialRepository = FakeMaterialRepository(listOf(Material(cottonMaterialId, "Cotton"))),
            fabricRepository = FakeFabricRepository(listOf(Fabric(jerseyFabricId, "Jersey"))),
            occasionRepository = FakeOccasionRepository(listOf(Occasion(casualOccasionId, "Casual"))),
        )

    @Test
    fun `auto-save countdown starts and saves once every required field is HIGH confidence or N slash A`() =
        runTest {
            val queueItemId = autoSaveReadyItem(fullRequiredConfidenceSuggestions())
            val vm = viewModelWithFullReferenceData(queueItemId)

            vm.uiState.test {
                val counting = awaitMatching { it.autoSaveCountdownSeconds != null }
                assertEquals(3, counting.autoSaveCountdownSeconds)

                mainDispatcher.scheduler.advanceTimeBy(3_100L)
                mainDispatcher.scheduler.runCurrent()

                val saved = awaitMatching { it.didSave }
                assertTrue(saved.didSave)
                assertNull(saved.autoSaveCountdownSeconds)
            }
            assertEquals(1, garmentRepository.savedGarments.size)
            assertTrue(garmentRepository.savedGarments.single().isReviewed)
        }

    @Test
    fun `onCancelAutoSave stops the countdown without touching the auto-filled form or saving`() =
        runTest {
            val queueItemId = autoSaveReadyItem(fullRequiredConfidenceSuggestions())
            val vm = viewModelWithFullReferenceData(queueItemId)

            vm.uiState.test {
                val counting = awaitMatching { it.autoSaveCountdownSeconds != null }
                assertEquals(subcategoryId, counting.form.categoryId)

                vm.onCancelAutoSave()
                val cancelled = awaitMatching { it.autoSaveCountdownSeconds == null }
                assertNull(cancelled.autoSaveCountdownSeconds)
                // The AI-filled form survives cancellation untouched.
                assertEquals(subcategoryId, cancelled.form.categoryId)
                assertEquals(blueColorId, cancelled.form.primaryColorId)

                mainDispatcher.scheduler.advanceTimeBy(5_000L)
                mainDispatcher.scheduler.runCurrent()
            }
            assertEquals(0, garmentRepository.savedGarments.size)
        }

    @Test
    fun `auto-save never starts when one required field is only MEDIUM confidence`() =
        runTest {
            val mediumFabric = suggestion(MetadataField.FABRIC, "Jersey", 0.6f)
            val suggestions =
                fullRequiredConfidenceSuggestions().filterNot { it.field == MetadataField.FABRIC } +
                    mediumFabric
            val queueItemId = autoSaveReadyItem(suggestions)
            val vm = viewModelWithFullReferenceData(queueItemId)

            vm.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertNull(state.autoSaveCountdownSeconds)

                mainDispatcher.scheduler.advanceTimeBy(5_000L)
                mainDispatcher.scheduler.runCurrent()
            }
            assertEquals(0, garmentRepository.savedGarments.size)
        }

    private fun baseGarment(
        name: String,
        color: ColorId? = null,
    ): Garment {
        val now = Instant.now()
        return Garment(
            id = GarmentId(0),
            name = name,
            categoryId = topsCategoryId,
            primaryColorId = color,
            palette = emptyList(),
            materials = emptyList(),
            tagIds = emptyList(),
            seasons = emptySet(),
            dressCodes = emptySet(),
            pattern = null,
            fit = null,
            length = null,
            sleeveLength = null,
            warmthRating = null,
            breathabilityRating = null,
            brandId = null,
            size = null,
            price = null,
            purchaseDate = null,
            condition = null,
            careNotes = null,
            status = GarmentStatus.ACTIVE,
            isReviewed = true,
            isFavorite = false,
            images = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<GarmentReviewMetadataUiState>.awaitMatching(
        predicate: (GarmentReviewMetadataUiState) -> Boolean,
    ): GarmentReviewMetadataUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
