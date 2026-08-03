package com.wardrobe.app.feature.tryon.fakes

import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.BodyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal, module-local fake — same "real state in a `MutableStateFlow`,
 * no mocking framework" pattern `feature:trips`'s fakes file already uses. */
class FakeBodyProfileRepository : BodyProfileRepository {
    private val profile = MutableStateFlow<BodyProfile?>(null)
    private val measurements = MutableStateFlow<BodyMeasurements?>(null)
    val capturedPhotos = mutableListOf<Pair<BodyPose, String>>()
    var recomputeMeasurementsCallCount = 0
        private set

    override fun observeBodyProfile(): Flow<BodyProfile?> = profile

    override fun observeBodyMeasurements(): Flow<BodyMeasurements?> = measurements

    override suspend fun captureBodyPhoto(
        pose: BodyPose,
        sourceFilePath: String,
    ) {
        capturedPhotos += pose to sourceFilePath
    }

    override suspend fun deleteBodyProfile() {
        profile.value = null
        measurements.value = null
    }

    override suspend fun recomputeMeasurements() {
        recomputeMeasurementsCallCount++
    }
}
