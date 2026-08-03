package com.wardrobe.app.core.tryon.rendering

/**
 * A garment layer's width as a fraction of the canvas/background photo's
 * own width, before its own `scale` is applied — shared between the live
 * interactive `feature:tryon` `TryOnScreen` and `TryOnRenderCache`'s
 * non-interactive flattening compositor so a cached thumbnail matches the
 * live screen's own sizing convention rather than drifting independently.
 */
const val TRY_ON_LAYER_WIDTH_FRACTION = 0.55f
