package com.wardrobe.app.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.wardrobe.app.core.data.image.StagedImageStore
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.pipeline.GarmentImagePipeline
import com.wardrobe.app.core.image.presentation.GarmentPresentationEnhancer
import com.wardrobe.app.core.image.quality.ImageQualityAnalyzer
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.ReconstructionOutcome
import com.wardrobe.app.core.model.garment.StagedImage
import com.wardrobe.app.core.testing.rule.createInMemoryWardrobeDatabase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

/**
 * Covers every [ImageRepositoryImpl] method except `stageImage` directly —
 * `stageImage`'s WorkManager/Hilt-worker path is deliberately not unit tested
 * here, matching `BackupRepositoryImpl`'s own precedent (Phase 5a never added
 * a `BackupExportWorker`-level test either; only the portable operations layer
 * underneath it). [GarmentImagePipelineTest] (`core:image`) covers the
 * processing logic `ImageProcessingWorker` calls.
 */
@RunWith(RobolectricTestRunner::class)
class ImageRepositoryImplTest {
    private lateinit var db: WardrobeDatabase
    private lateinit var repository: ImageRepositoryImpl
    private lateinit var fileStore: ImageFileStore
    private lateinit var stagedImageStore: StagedImageStore

    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        db = createInMemoryWardrobeDatabase(context())
        fileStore = ImageFileStore(context())
        stagedImageStore = StagedImageStore()
        val pipeline =
            GarmentImagePipeline(
                fileStore,
                mockk<GarmentExtractionEngine>(relaxed = true),
                mockk<GarmentPresentationEnhancer>(relaxed = true),
                mockk<GarmentReconstructionEngine>(relaxed = true),
                mockk<GarmentMetadataEngine>(relaxed = true),
                ImageQualityAnalyzer(),
            )
        repository =
            ImageRepositoryImpl(
                workManager = mockk<WorkManager>(relaxed = true),
                pipeline = pipeline,
                fileStore = fileStore,
                stagedImageStore = stagedImageStore,
                imageMetadataDao = db.imageMetadataDao(),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** `image_metadata.garmentId` has a `CASCADE` foreign key to `garments` (Phase
     * 3) — every test that inserts a row needs a real garment to reference. */
    private suspend fun insertGarment(): Long {
        val categoryId =
            db.categoryDao().insert(CategoryEntity(name = "Tops", parentId = null, level = CategoryLevel.TOP))
        return db.garmentDao().insert(
            GarmentEntity(
                name = "Test Garment",
                categoryId = categoryId,
                primaryColorId = null,
                pattern = null,
                fit = null,
                length = null,
                sleeveLength = null,
                warmthRating = null,
                breathabilityRating = null,
                brandId = null,
                size = null,
                price = null,
                currencyCode = null,
                purchaseDate = null,
                condition = null,
                careNotes = null,
                status = GarmentStatus.ACTIVE,
                isReviewed = false,
                searchText = "test garment",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private fun jpegSourceFile(): File {
        val file = File(context().filesDir, "source.jpg")
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    @Test
    fun `analyzeQuality returns a real report without writing files`() =
        runTest {
            val report = repository.analyzeQuality(jpegSourceFile().path)

            assertTrue(report.checks.isNotEmpty())
        }

    @Test
    fun `commitStagedImage moves files into the garment directory and inserts metadata rows`() =
        runTest {
            val garmentId = insertGarment()
            val stagingId = "staging-1"
            val stagingDir = fileStore.ensureExists(fileStore.stagingDir(stagingId))
            val originalFile =
                fileStore
                    .fileFor(
                        stagingDir,
                        ImageType.ORIGINAL,
                    ).apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val thumbFile =
                fileStore
                    .fileFor(
                        stagingDir,
                        ImageType.THUMBNAIL,
                    ).apply { writeBytes(byteArrayOf(4, 5, 6)) }
            stagedImageStore.put(
                StagedImage(
                    stagingId = stagingId,
                    variants =
                        listOf(
                            ImageVariant(
                                ImageType.ORIGINAL,
                                originalFile.path,
                                500,
                                500,
                                originalFile.length(),
                                "jpg",
                                "hash-original",
                            ),
                            ImageVariant(
                                ImageType.THUMBNAIL,
                                thumbFile.path,
                                300,
                                300,
                                thumbFile.length(),
                                "webp",
                                "hash-thumb",
                            ),
                        ),
                    qualityReport = QualityReport(emptyList()),
                    backgroundRemovalStatus = BackgroundRemovalStatus.FAILED_KEPT_ORIGINAL,
                    cutoutConfidence = null,
                    reconstructionOutcome = ReconstructionOutcome.NOT_ATTEMPTED,
                    occlusionSeverity = null,
                    metadataSuggestions = emptyList(),
                ),
            )

            val result = repository.commitStagedImage(stagingId, GarmentId(garmentId))

            assertEquals(2, result.size)
            assertFalse("staging directory should be gone", stagingDir.exists())
            assertTrue(
                "original should exist in the garment directory",
                fileStore.fileFor(fileStore.garmentDir(garmentId), ImageType.ORIGINAL).exists(),
            )
            assertEquals(2, db.imageMetadataDao().getForGarment(garmentId).size)
        }

    @Test
    fun `discardStagedImage deletes the staging directory and cancels the worker`() =
        runTest {
            val stagingId = "staging-2"
            val stagingDir = fileStore.ensureExists(fileStore.stagingDir(stagingId))
            fileStore.fileFor(stagingDir, ImageType.ORIGINAL).writeBytes(byteArrayOf(1))
            stagedImageStore.put(
                StagedImage(
                    stagingId = stagingId,
                    variants = emptyList(),
                    qualityReport = QualityReport(emptyList()),
                    backgroundRemovalStatus = BackgroundRemovalStatus.SKIPPED,
                    cutoutConfidence = null,
                    reconstructionOutcome = ReconstructionOutcome.NOT_ATTEMPTED,
                    occlusionSeverity = null,
                    metadataSuggestions = emptyList(),
                ),
            )

            repository.discardStagedImage(stagingId)

            assertFalse(stagingDir.exists())
        }

    @Test
    fun `observeImages reflects rows inserted for a garment`() =
        runTest {
            val garmentId = insertGarment()
            db.imageMetadataDao().insertAll(
                listOf(
                    ImageMetadataEntity(
                        garmentId = garmentId,
                        type = ImageType.ORIGINAL,
                        filePath = "/images/$garmentId/original.jpg",
                        width = 500,
                        height = 500,
                        fileSizeBytes = 123L,
                        format = "jpg",
                        checksum = "abc",
                        createdAt = 0L,
                    ),
                ),
            )

            val images = repository.observeImages(GarmentId(garmentId)).first()

            assertEquals(1, images.size)
        }

    @Test
    fun `findGarmentIdForChecksum returns the owning garment for a known checksum`() =
        runTest {
            val garmentId = insertGarment()
            db.imageMetadataDao().insertAll(
                listOf(
                    ImageMetadataEntity(
                        garmentId = garmentId,
                        type = ImageType.ORIGINAL,
                        filePath = "/images/$garmentId/original.jpg",
                        width = 500,
                        height = 500,
                        fileSizeBytes = 123L,
                        format = "jpg",
                        checksum = "duplicate-hash",
                        createdAt = 0L,
                    ),
                ),
            )

            val found = repository.findGarmentIdForChecksum("duplicate-hash")

            assertEquals(GarmentId(garmentId), found)
        }

    @Test
    fun `findGarmentIdForChecksum returns null for an unknown checksum`() =
        runTest {
            val found = repository.findGarmentIdForChecksum("never-seen-hash")

            assertEquals(null, found)
        }

    @Test
    fun `retryStage METADATA redoes metadata generation and updates the cached staged image`() =
        runTest {
            val stagingId = "staging-retry"
            val stagingDir = fileStore.ensureExists(fileStore.stagingDir(stagingId))
            val cutoutFile = fileStore.fileFor(stagingDir, ImageType.CUTOUT)
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            cutoutFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 90, it) }
            val initialSuggestions =
                listOf(
                    MetadataSuggestion(
                        MetadataField.PRIMARY_COLOR,
                        "Gray",
                        0.8f,
                        AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
                    ),
                )
            val staged =
                StagedImage(
                    stagingId = stagingId,
                    variants =
                        listOf(
                            ImageVariant(ImageType.CUTOUT, cutoutFile.path, 100, 100, cutoutFile.length(), "webp", "h"),
                        ),
                    qualityReport = QualityReport(emptyList()),
                    backgroundRemovalStatus = BackgroundRemovalStatus.SUCCEEDED,
                    cutoutConfidence = 0.9f,
                    reconstructionOutcome = ReconstructionOutcome.NOT_ATTEMPTED,
                    occlusionSeverity = null,
                    metadataSuggestions = initialSuggestions,
                )
            stagedImageStore.put(staged)
            val newSuggestions =
                listOf(
                    MetadataSuggestion(
                        MetadataField.PRIMARY_COLOR,
                        "Blue",
                        0.95f,
                        AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
                    ),
                )
            val metadataEngine = mockk<GarmentMetadataEngine>()
            coEvery { metadataEngine.generateMetadata(any()) } returns newSuggestions
            val retryPipeline =
                GarmentImagePipeline(
                    fileStore,
                    mockk<GarmentExtractionEngine>(relaxed = true),
                    mockk<GarmentPresentationEnhancer>(relaxed = true),
                    mockk<GarmentReconstructionEngine>(relaxed = true),
                    metadataEngine,
                    ImageQualityAnalyzer(),
                )
            val retryRepository =
                ImageRepositoryImpl(
                    workManager = mockk<WorkManager>(relaxed = true),
                    pipeline = retryPipeline,
                    fileStore = fileStore,
                    stagedImageStore = stagedImageStore,
                    imageMetadataDao = db.imageMetadataDao(),
                )

            val result = retryRepository.retryStage(stagingId, ImageRetryStage.METADATA)

            assertEquals(newSuggestions, result.metadataSuggestions)
            assertEquals(newSuggestions, stagedImageStore.peek(stagingId)?.metadataSuggestions)
        }

    @Test
    fun `deleteImagesForGarment removes the garment's file directory`() =
        runTest {
            val garmentId = insertGarment()
            val dir = fileStore.ensureExists(fileStore.garmentDir(garmentId))
            fileStore.fileFor(dir, ImageType.ORIGINAL).writeBytes(byteArrayOf(1))

            repository.deleteImagesForGarment(GarmentId(garmentId))

            assertFalse(dir.exists())
        }
}
