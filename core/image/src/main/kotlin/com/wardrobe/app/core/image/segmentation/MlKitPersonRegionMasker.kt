package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.hypot

/** Below this per-landmark `inFrameLikelihood`, a landmark is treated as
 * absent — same discipline as `core:tryon`'s `MlKitBodyAnchorEstimator`. */
private const val MIN_LANDMARK_LIKELIHOOD = 0.5f

/** Region sizes are expressed as a fraction of shoulder width — the one
 * distance this app can reliably compute from a single photo without a
 * camera-calibrated reference scale. These fractions are this project's own
 * editorial choice (a real, roughly head/arm-proportioned heuristic), not an
 * ML Kit or anatomical constant — disclosed as approximate in
 * [PersonRegionMasker]'s KDoc.
 *
 * M25 real-device finding: [HEAD_RADIUS_SHOULDER_FRACTION] was `0.55f`,
 * which made the head punch-out's *radius* roughly as large as a whole head
 * *width* — an adult head is closer to a third of shoulder width, not over
 * half of it. Centered on the nose (below the eye line, already low on the
 * face), that oversized circle routinely ate into the collar, neckline, and
 * upper chest of the actual garment — the "garment regions missing" defect.
 * `0.30f` keeps full head+hair coverage without reaching the chest for
 * typical adult proportions; [HEAD_CENTER_UPWARD_FRACTION] additionally
 * lifts the circle's center off the nose toward the head's true vertical
 * middle, since nothing here has a real "top of head" landmark to anchor to
 * directly. */
private const val HEAD_RADIUS_SHOULDER_FRACTION = 0.30f
private const val HEAD_CENTER_UPWARD_FRACTION = 0.5f
private const val LIMB_WIDTH_SHOULDER_FRACTION = 0.35f
private const val HAND_ONLY_RADIUS_SHOULDER_FRACTION = 0.22f
private const val HAND_EXTENSION_FRACTION = 0.35f

@Singleton
class MlKitPersonRegionMasker
    @Inject
    constructor() : PersonRegionMasker {
        private val detector by lazy {
            val options =
                PoseDetectorOptions
                    .Builder()
                    .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .build()
            PoseDetection.getClient(options)
        }

        override suspend fun mask(
            cutout: Bitmap,
            originalPhoto: Bitmap,
        ): Bitmap {
            val regions = detectPose(originalPhoto)?.let(::buildPunchOutRegions)?.takeIf { it.isNotEmpty() }
            return if (regions != null) applyPunchOuts(cutout, regions) else cutout
        }

        private suspend fun detectPose(bitmap: Bitmap): Pose? =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                detector
                    .process(input)
                    .addOnSuccessListener { pose -> continuation.resume(pose) }
                    .addOnFailureListener { continuation.resume(null) }
            }
    }

internal sealed interface PunchOutRegion {
    data class Circle(
        val cx: Float,
        val cy: Float,
        val radius: Float,
    ) : PunchOutRegion

    data class Capsule(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val strokeWidth: Float,
    ) : PunchOutRegion
}

private fun Pose.confidentLandmark(type: Int): PoseLandmark? =
    getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD }

/** Every region below is derived only from real landmark coordinates ML Kit
 * reported with confidence above [MIN_LANDMARK_LIKELIHOOD] — no region is
 * drawn from a guessed position (Constitution rule 4). Both shoulders are
 * required as the scale reference; without them there is no reliable way to
 * size a region at all, so masking is skipped entirely rather than guessed. */
private fun buildPunchOutRegions(pose: Pose): List<PunchOutRegion> {
    val shoulderWidth = shoulderWidth(pose) ?: return emptyList()
    return buildList {
        headRegion(pose, shoulderWidth)?.let(::add)
        addAll(armRegion(pose, shoulderWidth, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST))
        addAll(armRegion(pose, shoulderWidth, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST))
    }
}

private fun shoulderWidth(pose: Pose): Float? {
    val leftShoulder = pose.confidentLandmark(PoseLandmark.LEFT_SHOULDER)
    val rightShoulder = pose.confidentLandmark(PoseLandmark.RIGHT_SHOULDER)
    if (leftShoulder == null || rightShoulder == null) return null
    val width =
        hypot(
            (leftShoulder.position.x - rightShoulder.position.x).toDouble(),
            (leftShoulder.position.y - rightShoulder.position.y).toDouble(),
        ).toFloat()
    return width.takeIf { it > 0f }
}

private fun headRegion(
    pose: Pose,
    shoulderWidth: Float,
): PunchOutRegion.Circle? {
    val head =
        pose.confidentLandmark(PoseLandmark.NOSE)
            ?: pose.confidentLandmark(PoseLandmark.LEFT_EYE)
            ?: pose.confidentLandmark(PoseLandmark.RIGHT_EYE)
            ?: return null
    return computeHeadCircle(head.position.x, head.position.y, shoulderWidth)
}

/** Pure geometry, split out from [headRegion] so the corrected sizing/
 * placement (M25 real-device finding, see [HEAD_RADIUS_SHOULDER_FRACTION]'s
 * KDoc) is directly testable without constructing ML Kit's [Pose]. */
internal fun computeHeadCircle(
    headLandmarkX: Float,
    headLandmarkY: Float,
    shoulderWidth: Float,
): PunchOutRegion.Circle {
    val radius = shoulderWidth * HEAD_RADIUS_SHOULDER_FRACTION
    return PunchOutRegion.Circle(
        cx = headLandmarkX,
        cy = headLandmarkY - radius * HEAD_CENTER_UPWARD_FRACTION,
        radius = radius,
    )
}

private fun armRegion(
    pose: Pose,
    shoulderWidth: Float,
    elbowType: Int,
    wristType: Int,
): List<PunchOutRegion> {
    val wrist = pose.confidentLandmark(wristType) ?: return emptyList()
    val elbow = pose.confidentLandmark(elbowType)
    return if (elbow == null) {
        listOf(
            PunchOutRegion.Circle(
                cx = wrist.position.x,
                cy = wrist.position.y,
                radius = shoulderWidth * HAND_ONLY_RADIUS_SHOULDER_FRACTION,
            ),
        )
    } else {
        val dx = wrist.position.x - elbow.position.x
        val dy = wrist.position.y - elbow.position.y
        listOf(
            PunchOutRegion.Capsule(
                x1 = elbow.position.x,
                y1 = elbow.position.y,
                x2 = wrist.position.x + dx * HAND_EXTENSION_FRACTION,
                y2 = wrist.position.y + dy * HAND_EXTENSION_FRACTION,
                strokeWidth = shoulderWidth * LIMB_WIDTH_SHOULDER_FRACTION,
            ),
        )
    }
}

private fun applyPunchOuts(
    source: Bitmap,
    regions: List<PunchOutRegion>,
): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            strokeCap = Paint.Cap.ROUND
        }
    regions.forEach { region ->
        when (region) {
            is PunchOutRegion.Circle -> {
                canvas.drawCircle(region.cx, region.cy, region.radius, paint)
            }

            is PunchOutRegion.Capsule -> {
                paint.strokeWidth = region.strokeWidth
                canvas.drawLine(region.x1, region.y1, region.x2, region.y2, paint)
            }
        }
    }
    return result
}
