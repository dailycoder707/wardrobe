package com.wardrobe.app.core.ai.job

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

private const val PUMP_TIMEOUT_MS = 5_000L
private const val PUMP_STEP_MS = 10L

@RunWith(RobolectricTestRunner::class)
class AiJobManagerTest {
    private lateinit var context: Context
    private lateinit var aiJobDao: FakeAiJobDao
    private lateinit var manager: AiJobManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val registry = AiWorkRegistry()
        val config =
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(TestAiWorkerFactory(registry))
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        aiJobDao = FakeAiJobDao()
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        manager = AiJobManager(WorkManager.getInstance(context), registry, aiJobDao, clock)
    }

    @Test
    fun `dispatch returns the block's result and records the job as succeeded`() {
        val result =
            pumpLooperUntilComplete(context) {
                manager.dispatch(AiCapability.GARMENT_METADATA, "cache-key-1") { "hello" }
            }

        assertEquals("hello", result.value)
        assertEquals(true, result.isOwner)
        val job = runBlocking { aiJobDao.getByCacheKey("cache-key-1") }
        assertEquals(AiJobStatus.SUCCEEDED, job?.status)
    }

    @Test
    fun `dispatch inserts a RUNNING row before the underlying work is invoked`() {
        var statusWhenBlockRan: AiJobStatus? = null

        pumpLooperUntilComplete(context) {
            manager.dispatch(AiCapability.GARMENT_EXTRACTION, "cache-key-2") {
                statusWhenBlockRan = aiJobDao.getByCacheKey("cache-key-2")?.status
                "done"
            }
        }

        assertEquals(AiJobStatus.RUNNING, statusWhenBlockRan)
    }

    @Test
    fun `two concurrent dispatches for the identical cache key coalesce into one call`() {
        val callCount = AtomicInteger(0)

        val (first, second) =
            pumpLooperUntilBothComplete(context) {
                manager.dispatch(AiCapability.OUTFIT_STYLING, "cache-key-shared") {
                    callCount.incrementAndGet()
                    "shared-result"
                }
            }

        assertEquals(1, callCount.get())
        assertEquals("shared-result", first.value)
        assertEquals("shared-result", second.value)
        assertEquals(1, listOf(first, second).count { it.isOwner })
        val job = runBlocking { aiJobDao.getByCacheKey("cache-key-shared") }
        assertEquals(AiJobStatus.SUCCEEDED, job?.status)
    }
}

private class TestAiWorkerFactory(
    private val registry: AiWorkRegistry,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == AiCapabilityWorker::class.java.name) {
            AiCapabilityWorker(appContext, workerParameters, registry)
        } else {
            null
        }
}

/**
 * Exposed for [com.wardrobe.app.core.ai.gateway.DefaultAiGatewayTest], which
 * needs the same real-`AiJobManager`/`WorkManager` integration helper.
 * WorkManager's own internal scheduling posts to the (Robolectric-shadowed)
 * main looper even with [SynchronousExecutor] configured for the task
 * executor, and [AiJobManager]'s `NetworkType.CONNECTED` constraint is never
 * automatically considered met under [WorkManagerTestInitHelper] — both need
 * an explicit pump/satisfy step, or [AiCapabilityWorker] never actually
 * starts and `dispatch`'s `CompletableDeferred` never completes.
 */
internal fun <T> pumpLooperUntilComplete(
    context: Context,
    timeoutMs: Long = PUMP_TIMEOUT_MS,
    block: suspend () -> T,
): T =
    runBlocking {
        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        val deferred = async(Dispatchers.IO) { block() }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!deferred.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            testDriver?.let { driver ->
                workManager
                    .getWorkInfosByTag(AiJobManager.WORK_TAG)
                    .get()
                    .filter { it.state == WorkInfo.State.ENQUEUED }
                    .forEach { driver.setAllConstraintsMet(it.id) }
            }
            if (System.currentTimeMillis() > deadline) {
                error("Timed out waiting for dispatch to complete")
            }
            Thread.sleep(PUMP_STEP_MS)
        }
        deferred.await()
    }

/** Same pumping contract as [pumpLooperUntilComplete], but launches [block]
 * twice concurrently — used to prove [AiJobManager.dispatch] coalesces two
 * simultaneous requests for the identical cache key into a single in-flight
 * job rather than dispatching (and paying for) the underlying work twice. */
internal fun <T> pumpLooperUntilBothComplete(
    context: Context,
    timeoutMs: Long = PUMP_TIMEOUT_MS,
    block: suspend () -> T,
): Pair<T, T> =
    runBlocking {
        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        val first = async(Dispatchers.IO) { block() }
        val second = async(Dispatchers.IO) { block() }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!first.isCompleted || !second.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            testDriver?.let { driver ->
                workManager
                    .getWorkInfosByTag(AiJobManager.WORK_TAG)
                    .get()
                    .filter { it.state == WorkInfo.State.ENQUEUED }
                    .forEach { driver.setAllConstraintsMet(it.id) }
            }
            if (System.currentTimeMillis() > deadline) {
                error("Timed out waiting for both dispatches to complete")
            }
            Thread.sleep(PUMP_STEP_MS)
        }
        first.await() to second.await()
    }
