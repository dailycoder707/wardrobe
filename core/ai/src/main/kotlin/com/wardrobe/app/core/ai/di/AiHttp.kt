package com.wardrobe.app.core.ai.di

import javax.inject.Qualifier

/** Distinguishes this module's `OkHttpClient`/`Retrofit`/`Json` instances
 * (`core:ai`'s vendor-API surface, longer timeouts) from `core:data`'s
 * `NetworkModule` ones (Open-Meteo, shorter timeouts) — both are unqualified
 * types that would otherwise collide as `Dagger/DuplicateBindings` once both
 * modules sit in the same app-level Hilt component. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiHttp
