package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap

/**
 * Add-to-Wardrobe v2's heuristic refinement over [BackgroundRemover]'s
 * cutout: Subject Segmentation isolates "the salient subject," which for a
 * worn-garment photo is the *person*, not just the garment. This punches out
 * a best-effort head/hand/forearm region from that cutout using pose
 * landmarks — an honest approximation, not a dedicated clothing-segmentation
 * model. Disclosed limitation: works well for sleeveless/short-sleeve
 * garments photographed straight-on; degrades on complex poses, loose long
 * sleeves, or garments (sarees, scarves) that drape past the arm. Never
 * blocks extraction — a pose-detection failure returns the cutout
 * unmodified rather than throwing, since this is a refinement, not a
 * required step.
 */
interface PersonRegionMasker {
    /** [originalPhoto] is the pre-extraction photo (pose landmarks need real
     * body detail Subject Segmentation's own cutout no longer has once its
     * background is already transparent); [cutout] is the bitmap actually
     * modified and returned. */
    suspend fun mask(
        cutout: Bitmap,
        originalPhoto: Bitmap,
    ): Bitmap
}
