package com.wardrobe.app.core.tryon.pose

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs

/** Below this per-landmark `inFrameLikelihood`, a landmark is treated as
 * absent rather than trusted — the same "don't fabricate a number from a
 * low-confidence signal" discipline [BodyAnchorEstimate.Success]'s KDoc
 * describes. */
private const val MIN_LANDMARK_LIKELIHOOD = 0.5f

/**
 * The provisional default chosen this phase — see phase-10's "Pose
 * detection" section for why single-image mode (not streaming) is correct
 * here: this only ever runs once per guided-capture photo, never on a
 * camera preview frame stream. Every fraction below is a direct, real
 * function of ML Kit's own landmark coordinates — none are fabricated
 * (Constitution rule 4); a landmark pair that isn't confidently detected
 * simply yields a `null` fraction rather than a guessed one.
 *
 * [BodyAnchorEstimate.Success.waistHeightFraction] uses the hip landmarks as
 * a proxy — ML Kit's base pose model has no distinct waist landmark — the
 * same honest approximation [neckPositionYFraction] makes using the
 * shoulder line (no distinct neck landmark either).
 */
@Singleton
class MlKitBodyAnchorEstimator
    @Inject
    constructor() : BodyAnchorEstimator {
        private val detector by lazy {
            val options = PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
            PoseDetection.getClient(options)
        }

        override suspend fun estimate(bitmap: Bitmap): BodyAnchorEstimate =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                detector
                    .process(input)
                    .addOnSuccessListener { pose ->
                        continuation.resume(pose.toEstimate(bitmap.width, bitmap.height))
                    }.addOnFailureListener { exception ->
                        val reason = exception.message ?: "mlkit_pose_detection_failed"
                        continuation.resume(BodyAnchorEstimate.Failure(reason))
                    }
            }
    }

private fun Pose.landmark(type: Int): PoseLandmark? =
    getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD }

private fun xDiff(
    a: PoseLandmark?,
    b: PoseLandmark?,
): Float? = if (a == null || b == null) null else abs(a.position.x - b.position.x)

private fun avgY(
    a: PoseLandmark?,
    b: PoseLandmark?,
): Float? = if (a == null || b == null) null else (a.position.y + b.position.y) / 2f

private fun Pose.toEstimate(
    width: Int,
    height: Int,
): BodyAnchorEstimate {
    val leftShoulder = landmark(PoseLandmark.LEFT_SHOULDER)
    val rightShoulder = landmark(PoseLandmark.RIGHT_SHOULDER)
    val leftHip = landmark(PoseLandmark.LEFT_HIP)
    val rightHip = landmark(PoseLandmark.RIGHT_HIP)
    val leftAnkle = landmark(PoseLandmark.LEFT_ANKLE)
    val rightAnkle = landmark(PoseLandmark.RIGHT_ANKLE)

    val usedLandmarks = listOfNotNull(leftShoulder, rightShoulder, leftHip, rightHip, leftAnkle, rightAnkle)
    if (usedLandmarks.isEmpty()) return BodyAnchorEstimate.Failure("mlkit_no_confident_landmarks")

    val shoulderY = avgY(leftShoulder, rightShoulder)
    val hipY = avgY(leftHip, rightHip)
    val ankleY = avgY(leftAnkle, rightAnkle)

    return BodyAnchorEstimate.Success(
        shoulderWidthFraction = xDiff(leftShoulder, rightShoulder)?.let { it / width },
        torsoHeightFraction =
            if (shoulderY != null && hipY != null) abs(hipY - shoulderY) / height else null,
        waistHeightFraction = hipY?.let { it / height },
        hipWidthFraction = xDiff(leftHip, rightHip)?.let { it / width },
        neckPositionYFraction = shoulderY?.let { it / height },
        anklePositionYFraction = ankleY?.let { it / height },
        confidence = usedLandmarks.map { it.inFrameLikelihood }.average().toFloat(),
    )
}
