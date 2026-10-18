package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.BrainPalDatabase
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Module 3: Daily periodic WorkManager job that enforces the strict 4-week (28-day)
 * rolling retention policy and keeps total SQLite storage well under 5 MB.
 */
class PruneDatabaseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cutoffEpoch = LocalDate.now().minusDays(RETENTION_DAYS).toEpochDay()
            val dao = BrainPalDatabase.getInstance(applicationContext).reelStatsDao()
            val purgedCount = dao.purgeAllOlderThan(cutoffEpoch)
            Log.d(TAG, "Pruned $purgedCount daily records older than epochDay=$cutoffEpoch")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune rolling 28-day database", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PruneDatabaseWorker"
        const val UNIQUE_WORK_NAME = "brainpal_daily_28d_prune_work"
        const val RETENTION_DAYS = 27L // Today + 27 previous days = 28-day rolling window

        fun scheduleDailyPrune(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(
                        appContext,
                        Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
                    )
                }

                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<PruneDatabaseWorker>(
                    repeatInterval = 1,
                    repeatIntervalTimeUnit = TimeUnit.DAYS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { e ->
                Log.w(TAG, "Unable to schedule WorkManager job: ${e.message}")
            }
        }
    }
}
