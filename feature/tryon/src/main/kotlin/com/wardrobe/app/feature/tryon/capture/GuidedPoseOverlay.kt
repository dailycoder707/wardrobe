package com.wardrobe.app.feature.tryon.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.tryon.BodyPose

private fun instructionFor(pose: BodyPose): String =
    when (pose) {
        BodyPose.NEUTRAL -> "Stand naturally, facing the camera, arms relaxed at your sides."
        BodyPose.ARMS_OUT -> "Raise your arms slightly out to your sides — this helps pin down your shoulders."
        BodyPose.TORSO -> "Step closer so your waist and hips fill the frame."
        BodyPose.FEET -> "Step back so your feet and lower legs are clearly visible."
    }

/** The per-pose guidance text shown above the camera preview during guided
 * body-profile capture — see `phase-10-personal-virtual-tryon.md`'s guided-
 * capture design for why these four poses, in this order, and why multi-
 * angle capture was deliberately excluded from v1. */
@Composable
fun GuidedPoseOverlay(
    pose: BodyPose,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Column {
            Text("Step ${pose.ordinal + 1} of ${BodyPose.entries.size}", style = MaterialTheme.typography.labelLarge)
            Text(instructionFor(pose), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
