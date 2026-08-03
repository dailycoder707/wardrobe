package com.wardrobe.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.wardrobe.app.core.data.image.OrphanedImageCleanupWorker
import com.wardrobe.app.core.data.sync.SyncWorker
import com.wardrobe.app.core.data.weather.WeatherRefreshWorker
import com.wardrobe.app.core.ui.image.WardrobeImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Composition root for Hilt. Implements [Configuration.Provider] so
 * [HiltWorkerFactory] reaches `BackupExportWorker`/`BackupRestoreWorker`
 * (Phase 5a), `ImageProcessingWorker`/`OrphanedImageCleanupWorker`
 * (Phase 5b), and `WeatherRefreshWorker` (Phase 7) — this requires the
 * manifest surgery documented in
 * `AndroidManifest.xml` (`WorkManagerInitializer` removal), since a custom
 * [Configuration.Provider] conflicts with WorkManager's default,
 * non-Hilt-aware on-demand initializer rather than being picked up by it.
 *
 * Also implements [SingletonImageLoader.Factory] so every `AsyncImage` in the
 * app shares one bounded-cache Coil `ImageLoader` (`core:ui`'s
 * [WardrobeImageLoaderFactory], see phase-5b-image-pipeline.md's "Caching"
 * section) instead of Coil's unbounded default.
 */
@HiltAndroidApp
class WardrobeApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = WardrobeImageLoaderFactory.create(context)

    override fun onCreate() {
        super.onCreate()
        OrphanedImageCleanupWorker.schedule(WorkManager.getInstance(this))
        WeatherRefreshWorker.schedule(WorkManager.getInstance(this))
        // Wi-Fi-only/charging-only defaults, same as WeatherRefreshWorker
        // above — the real, possibly-edited SyncPreferences take effect the
        // next time SyncViewModel.updatePreferences calls
        // SyncScheduler.reschedule; reading DataStore here would need a
        // suspend call this non-suspend onCreate() can't make.
        SyncWorker.schedulePeriodic(WorkManager.getInstance(this), wifiOnly = true, chargingOnly = false)
    }
}
