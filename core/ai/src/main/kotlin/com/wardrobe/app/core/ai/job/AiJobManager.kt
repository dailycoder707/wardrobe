package com.wardrobe.app.core.ai.job

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.wardrobe.app.core.database.dao.AiJobDao
import com.wardrobe.app.core.database.entity.AiJobEntity
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No screen or capability engine calls a cloud provider directly — every
 * [com.wardrobe.app.core.ai.gateway.AiGateway] dispatch goes through this
 * (ADR-012 §3). Built on WorkManager rather than a hand-rolled queue: this
 * app already uses WorkManager for exactly this kind of resumable
 * background work (`core:data`'s `ImageProcessingWorker`), and WorkManager
 * already natively provides retry/backoff/offline-handling/progress —
 * reusing it is less code and less risk than reinventing it. `ai_jobs`
 * (via [AiJobDao]) is the generalized job ledger this creates rows in, for
 * observability — see [AiWorkRegistry]'s KDoc for the honest limit on what
 * "resumable" means here.
 *
 * [dispatch] is keyed by the Gateway's own cache key, not a fresh id per
 * call: two concurrent dispatches for the identical cache key (a
 * double-tapped action, or a screen recomposition re-triggering the same
 * request before the first reply lands) share the single in-flight
 * WorkManager job and its result instead of each firing its own duplicate
 * cloud call. Only the caller that actually wins ownership of that in-flight
 * entry ([AiWorkRegistry.registerIfAbsent]) inserts/updates the `ai_jobs`
 * ledger row; a caller that joins an existing in-flight entry just awaits
 * its result.
 *
 * [dispatch] returns that ownership flag ([Dispatched.isOwner]) alongside
 * the value precisely so [com.wardrobe.app.core.ai.gateway.DefaultAiGateway]
 * can do the same thing for its own post-dispatch side effects (writing the
 * `ai_result_cache` row, recording an [com.wardrobe.app.core.ai.metrics.AiMetrics]
 * event): only the owner should, or a joined caller would redundantly
 * persist a second cache row and a second metric event for a call that only
 * happened once.
 */
@Singleton
class AiJobManager
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val registry: AiWorkRegistry,
        private val aiJobDao: AiJobDao,
        private val clock: Clock,
    ) {
        /** @param isOwner whether this caller won ownership of the in-flight
         * request (see [AiJobManager]'s KDoc) — `false` means another
         * concurrent caller for the identical cache key is the one that
         * should record any one-time-only side effect for [value]. */
        data class Dispatched<T>(
            val value: T,
            val isOwner: Boolean,
        )

        suspend fun <T> dispatch(
            capability: AiCapability,
            cacheKey: String,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            block: suspend () -> T,
        ): Dispatched<T> {
            val candidate = PendingAiWork(block = { block() })
            val owned = registry.registerIfAbsent(cacheKey, candidate)
            val isOwner = owned === candidate
            if (isOwner) startOwnedWork(capability, cacheKey, timeoutMs)
            return Dispatched(awaitResult(cacheKey, owned, isOwner), isOwner)
        }

        private suspend fun startOwnedWork(
            capability: AiCapability,
            cacheKey: String,
            timeoutMs: Long,
        ) {
            insertPendingJob(capability, cacheKey)
            enqueueWorker(cacheKey, timeoutMs)
            markStatus(cacheKey, AiJobStatus.RUNNING, errorMessage = null)
        }

        /** [AiCapabilityWorker] only ever forwards an [IOException] or a
         * [TimeoutCancellationException] to the deferred it completes
         * exceptionally with — those are the two failure modes retry/backoff
         * actually apply to. Anything else propagates uncaught, leaving this
         * job's ledger row at `RUNNING` — an honest, disclosed gap for a
         * genuinely unexpected error rather than a swallowed one. Only the
         * owning caller touches the ledger; a joined caller only relays the
         * result/error it was given. */
        private suspend fun <T> awaitResult(
            cacheKey: String,
            pending: PendingAiWork,
            isOwner: Boolean,
        ): T {
            try {
                @Suppress("UNCHECKED_CAST")
                val result = pending.deferred.await() as T
                if (isOwner) markStatus(cacheKey, AiJobStatus.SUCCEEDED, errorMessage = null)
                return result
            } catch (error: IOException) {
                if (isOwner) markStatus(cacheKey, AiJobStatus.FAILED, error.message)
                throw error
            } catch (error: TimeoutCancellationException) {
                if (isOwner) markStatus(cacheKey, AiJobStatus.FAILED, error.message)
                throw error
            }
        }

        private suspend fun insertPendingJob(
            capability: AiCapability,
            cacheKey: String,
        ) {
            val now = clock.millis()
            aiJobDao.insert(
                AiJobEntity(
                    capability = capability,
                    cacheKey = cacheKey,
                    status = AiJobStatus.PENDING,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        private fun enqueueWorker(
            cacheKey: String,
            timeoutMs: Long,
        ) {
            val request =
                OneTimeWorkRequestBuilder<AiCapabilityWorker>()
                    .setInputData(workDataOf(KEY_JOB_ID to cacheKey, KEY_TIMEOUT_MS to timeoutMs))
                    .addTag(WORK_TAG)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()
            workManager.enqueueUniqueWork(cacheKey, ExistingWorkPolicy.KEEP, request)
        }

        private suspend fun markStatus(
            cacheKey: String,
            status: AiJobStatus,
            errorMessage: String?,
        ) {
            val existing = aiJobDao.getByCacheKey(cacheKey) ?: return
            aiJobDao.update(existing.copy(status = status, errorMessage = errorMessage, updatedAt = clock.millis()))
        }

        companion object {
            private const val DEFAULT_TIMEOUT_MS = 30_000L

            /** Exposed so tests can find in-flight requests via
             * `WorkManager.getWorkInfosByTag` and (under
             * `WorkManagerTestInitHelper`) satisfy their `NetworkType.CONNECTED`
             * constraint explicitly — Robolectric's test WorkManager never
             * infers real connectivity on its own. */
            internal const val WORK_TAG = "ai_capability_worker"
        }
    }
