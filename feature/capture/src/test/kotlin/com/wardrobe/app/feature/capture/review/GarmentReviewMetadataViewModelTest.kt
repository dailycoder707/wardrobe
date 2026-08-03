package com.wardrobe.app.feature.capture.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import com.wardrobe.app.feature.capture.fakes.FakeBrandRepository
import com.wardrobe.app.feature.capture.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.capture.fakes.FakeColorRepository
import com.wardrobe.app.feature.capture.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.capture.fakes.FakeImageRepository
import com.wardrobe.app.feature.capture.fakes.FakeImportQueueRepository
import com.wardrobe.app.feature.capture.fakes.FakeMaterialRepository
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

    private val topsCategoryId = CategoryId(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        importQueueRepository = FakeImportQueueRepository()
        imageRepository = FakeImageRepository()
        garmentRepository = FakeGarmentRepository()
        categoryRepository =
            FakeCategoryRepository(
                listOf(Category(topsCategoryId, "Tops", null, CategoryLevel.TOP)),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedReadyItem(
        stagingId: String = "staging-1",
        checksum: String = "hash-1",
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
                qualityReport = QualityReport(emptyList()),
                backgroundRemovalStatus = BackgroundRemovalStatus.SUCCEEDED,
                cutoutConfidence = 0.9f,
            )
        return item.id
    }

    private fun viewModel(queueItemId: Long) =
        GarmentReviewMetadataViewModel(
            savedStateHandle = SavedStateHandle(mapOf("queueItemId" to queueItemId)),
            importQueueRepository = importQueueRepository,
            imageRepository = imageRepository,
            garmentRepository = garmentRepository,
            categoryRepository = categoryRepository,
            brandRepository = FakeBrandRepository(),
            colorRepository = FakeColorRepository(),
            materialRepository = FakeMaterialRepository(),
            tagRepository = FakeTagRepository(),
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
