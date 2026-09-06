package com.wardrobe.app.feature.settings.di

import javax.inject.Qualifier

/** Qualifies the app's real `BuildConfig.VERSION_NAME` string, bound in the
 * `app` module (the only module with that `BuildConfig` field, since it's
 * the sole `com.android.application` module) — declared here so `app` can
 * reference this type without `feature:settings` ever depending on `app`
 * itself. Same "narrow qualifier to avoid an unqualified-`String` collision"
 * reasoning as `core:ai`'s `AiHttp`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVersion
