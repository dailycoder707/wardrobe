package com.wardrobe.app.core.data.repository

import androidx.work.WorkManager
import com.wardrobe.app.core.data.weather.WeatherRefreshWorker
import com.wardrobe.app.core.domain.repository.WeatherRefreshScheduler
import javax.inject.Inject

class WeatherRefreshSchedulerImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : WeatherRefreshScheduler {
        override fun reschedule(intervalHours: Int) {
            WeatherRefreshWorker.schedule(workManager, intervalHours.toLong())
        }
    }
