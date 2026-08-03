package com.wardrobe.app.core.data.sync

import androidx.work.WorkManager
import com.wardrobe.app.core.domain.repository.SyncScheduler
import javax.inject.Inject

class SyncSchedulerImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : SyncScheduler {
        override fun reschedule(
            wifiOnly: Boolean,
            chargingOnly: Boolean,
        ) {
            SyncWorker.schedulePeriodic(workManager, wifiOnly, chargingOnly)
        }

        override fun syncNow() {
            SyncWorker.enqueueManual(workManager)
        }
    }
