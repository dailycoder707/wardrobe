package com.wardrobe.app.core.ai.di

import com.wardrobe.app.core.model.ai.AiVendor
import dagger.MapKey

/** Dagger has no built-in enum map key (only `@StringKey`/`@ClassKey`/etc.)
 * — this is the one this project's `Map<AiVendor, VisionPromptAdapter>` /
 * `Map<AiVendor, ImageTaskAdapter>` multibindings use, so each vendor
 * adapter (M6) is registered with `@IntoMap @AiVendorKey(AiVendor.X)`. */
@MapKey
annotation class AiVendorKey(
    val value: AiVendor,
)
