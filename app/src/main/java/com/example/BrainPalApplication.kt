package com.example

import android.app.Application
import com.example.data.BrainPalDatabase
import com.example.data.ReelRepository
import com.example.worker.PruneDatabaseWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BrainPalApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: BrainPalDatabase by lazy {
        BrainPalDatabase.getInstance(this)
    }

    val repository: ReelRepository by lazy {
        ReelRepository.getInstance(database.reelStatsDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Schedule daily 28-day retention cleanup job via WorkManager
        PruneDatabaseWorker.scheduleDailyPrune(this)

        // Also run an immediate non-blocking prune check on app startup
        applicationScope.launch {
            repository.pruneOlderThan28Days()
        }
    }
}
