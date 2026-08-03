package com.wardrobe.app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * docs/design/motion-guide.md — "unhurried, confident, never bouncy," exactly
 * two easing curves. Every duration constant below matches the guide's own
 * table; screens that need a transition not named here should pick the
 * closest matching category rather than inventing a new number.
 */
object WardrobeMotion {
    val calmOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val calmInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    const val DURATION_MICRO = 120
    const val DURATION_SMALL = 150
    const val DURATION_STANDARD = 240
    const val DURATION_LARGE = 350
    const val DURATION_EMPHASIS = 500

    /** The motion guide's "reduced motion" fallback: a plain cross-fade, no
     * scale/slide/rotation, per-transition rather than a global switch. */
    const val REDUCED_MOTION_DURATION = 150
}

/**
 * Unlike View-based `Animator`, Compose's animation APIs (`animateFloatAsState`
 * and friends) do not automatically honor
 * `Settings > Accessibility > Remove animations` — that setting maps to
 * `Settings.Global.ANIMATOR_DURATION_SCALE` being `0`, and a Compose app has to
 * read it explicitly and short-circuit its own animation durations, which is
 * exactly what this function is for (phase-4-design-system.md Section 10 /
 * `motion-guide.md`'s "per-transition requirement, not a global toggle").
 * Available on every API level this app supports (minSdk 26) — no version gate
 * needed.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
