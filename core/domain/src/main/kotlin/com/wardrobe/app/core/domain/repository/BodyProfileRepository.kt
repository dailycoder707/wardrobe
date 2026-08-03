package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.model.tryon.BodyProfile
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10 — the single, private, on-device body profile (single-per-device
 * for v1, see [BodyProfile]'s KDoc). Guided capture calls [captureBodyPhoto]
 * once per [BodyPose]; nothing here ever performs pose estimation itself —
 * see [recomputeMeasurements].
 */
interface BodyProfileRepository {
    fun observeBodyProfile(): Flow<BodyProfile?>

    /** `null` until [recomputeMeasurements] has run at least once for the
     * current profile — see [BodyMeasurements]'s KDoc for why this is a
     * separate stream from [observeBodyProfile], not a field on it. */
    fun observeBodyMeasurements(): Flow<BodyMeasurements?>

    /** Creates the single body profile on first call; every later call
     * against the same [pose] replaces that pose's photo. Does not itself
     * trigger [recomputeMeasurements] — the guided-capture flow calls that
     * once, after every pose has been captured. */
    suspend fun captureBodyPhoto(
        pose: BodyPose,
        sourceFilePath: String,
    )

    /** Deletes the profile row (cascading its photos/measurements) and its
     * on-disk photo/mask directory. */
    suspend fun deleteBodyProfile()

    /** The only place [BodyMeasurements] is ever written — runs on-device
     * pose estimation against the current profile's best available photo
     * and persists the result (even a real, honest all-`null` result on
     * estimation failure — see `MlKitBodyAnchorEstimator`'s KDoc). Never
     * called per-render; only when the profile's photos actually change. */
    suspend fun recomputeMeasurements()
}
