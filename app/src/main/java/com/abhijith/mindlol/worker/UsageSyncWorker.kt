package com.abhijith.mindlol.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abhijith.mindlol.MindLolApplication
import java.util.concurrent.TimeUnit

/**
 * Rebuilds the last 3 days from raw events into Room, in the background.
 *
 * Android keeps raw events for only about a week, so this job is what preserves
 * precise history. Rebuilding replaces each day's rows, so running it twice is harmless.
 */
class UsageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MindLolApplication).container
        return try {
            container.usageRepository.refresh(daysBack = 3) // does nothing if usage access is off
            Result.success()
        } catch (e: Exception) {
            Result.retry() // WorkManager tries again later with backoff
        }
    }

    companion object {
        private const val UNIQUE_NAME = "usage-sync"

        /**
         * Schedules the job about every 6 hours. KEEP: if it is already scheduled, leave it alone,
         * so calling this on every app start doesn't reset the timer.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true) // skip when the battery is low; it'll catch up later
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
