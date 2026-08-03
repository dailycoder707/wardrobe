package com.wardrobe.app.feature.capture.fakes

import com.wardrobe.app.core.domain.repository.ImageProcessingProgress
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImageMetadata
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf

class FakeImageRepository : ImageRepository {
    val stageImageCalls = mutableListOf<String>()
    val channelsByPath = mutableMapOf<String, Channel<ImageProcessingProgress>>()
    val committedStagingIds = mutableListOf<String>()
    val stagedImages = mutableMapOf<String, StagedImage>()
    var checksumOwner: GarmentId? = null

    override suspend fun analyzeQuality(sourceFilePath: String): QualityReport = QualityReport(emptyList())

    override fun stageImage(
        sourceFilePath: String,
        stagingId: String,
        cropRect: NormalizedRect?,
    ): Flow<ImageProcessingProgress> {
        stageImageCalls += sourceFilePath
        val channel = Channel<ImageProcessingProgress>(Channel.UNLIMITED)
        channelsByPath[sourceFilePath] = channel
        return channel.consumeAsFlow()
    }

    override suspend fun commitStagedImage(
        stagingId: String,
        garmentId: GarmentId,
    ): List<ImageMetadata> {
        committedStagingIds += stagingId
        return emptyList()
    }

    override suspend fun discardStagedImage(stagingId: String) = Unit

    override suspend fun peekStagedImage(stagingId: String): StagedImage? = stagedImages[stagingId]

    override suspend fun findGarmentIdForChecksum(checksum: String): GarmentId? = checksumOwner

    override fun observeImages(garmentId: GarmentId): Flow<List<ImageMetadata>> = flowOf(emptyList())

    override suspend fun deleteImagesForGarment(garmentId: GarmentId) = Unit
}
