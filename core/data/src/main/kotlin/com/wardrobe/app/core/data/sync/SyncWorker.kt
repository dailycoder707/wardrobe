package com.wardrobe.app.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wardrobe.app.core.domain.repository.SyncPreferencesRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val DEFAULT_INTERVAL_HOURS = 1L
private const val PERIODIC_WORK_NAME = "wardrobe_sync_periodic"
private const val MANUAL_WORK_NAME = "wardrobe_sync_manual"

/**
 * Runs one [SyncRepository.syncNow] attempt (Phase 8) — a no-op, still
 * `Result.success()`, when auto-sync is disabled in Wardrobe Sync settings,
 * the same "worker is an optimization, never a requirement" posture
 * `WeatherRefreshWorker` established in Phase 7. Battery/network
 * constraints ([Constraints]) come from [SyncPreferences][com.wardrobe.app.core.model.sync.SyncPreferences]
 * at *schedule* time (`SyncSchedulerImpl.reschedule`), not re-checked here —
 * WorkManager itself won't even start the worker if the constraints aren't
 * currently met.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncRepository: SyncRepository,
        private val syncPreferencesRepository: SyncPreferencesRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val prefs = syncPreferencesRepository.observePreferences().first()
            if (!prefs.autoSyncEnabled && !isManualRun()) return Result.success()
            syncRepository.syncNow()
            return Result.success()
        }

        private fun isManualRun(): Boolean = tags.contains(MANUAL_WORK_NAME)

        companion object {
            fun schedulePeriodic(
                workManager: WorkManager,
                wifiOnly: Boolean,
                chargingOnly: Boolean,
            ) {
                val request =
                    PeriodicWorkRequestBuilder<SyncWorker>(DEFAULT_INTERVAL_HOURS, TimeUnit.HOURS)
                        .addTag(SyncWorker::class.java.simpleName)
                        .setConstraints(buildConstraints(wifiOnly, chargingOnly))
                        .build()
                workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            }

            fun enqueueManual(workManager: WorkManager) {
                val request =
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .addTag(SyncWorker::class.java.simpleName)
                        .addTag(MANUAL_WORK_NAME)
                        .build()
                workManager.enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }

            private fun buildConstraints(
                wifiOnly: Boolean,
                chargingOnly: Boolean,
            ): Constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresCharging(chargingOnly)
                    .build()
        }
    }
