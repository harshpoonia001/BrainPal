package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class LiveSessionTelemetry(
    val activePackage: String? = null,
    val activeApp: MonitoredApp? = null,
    val isOverlayVisible: Boolean = false, // Strictly true ONLY when Instagram Reels or YouTube Shorts is actively open
    val sessionInstagramCount: Int = 0,
    val sessionYoutubeCount: Int = 0,
    val todayTotalReels: Int = 0,
    val brainState: BrainState = BrainState.HEALTHY,
    val lastIncrementTimestampMs: Long = 0L,
    val isAccessibilityServiceConnected: Boolean = false,
    val dailyGoalLimit: Int = 100
) {
    val sessionTotalReels: Int
        get() = sessionInstagramCount + sessionYoutubeCount
}

class ReelRepository(
    private val dao: ReelStatsDao
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _liveTelemetry = MutableStateFlow(LiveSessionTelemetry())
    val liveTelemetry: StateFlow<LiveSessionTelemetry> = _liveTelemetry.asStateFlow()

    init {
        repoScope.launch {
            val todayEpoch = LocalDate.now().toEpochDay()
            dao.observeDayStats(todayEpoch).collect { entity ->
                val total = entity?.totalReels ?: 0
                _liveTelemetry.update { current ->
                    current.copy(
                        todayTotalReels = total,
                        brainState = BrainState.fromCount(total)
                    )
                }
            }
        }
    }

    fun observeRolling28DayStats(today: LocalDate = LocalDate.now()): Flow<List<DailyReelStatsEntity>> {
        val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val oldestWeekMonday = currentWeekMonday.minusWeeks(3)
        return dao.observeStatsSince(oldestWeekMonday.toEpochDay())
    }

    fun observeWeeklyReports(): Flow<List<WeeklyReportEntity>> = dao.observeWeeklyReports()

    fun observeTodayStats(today: LocalDate = LocalDate.now()): Flow<DailyReelStatsEntity?> =
        dao.observeDayStats(today.toEpochDay())

    /**
     * Atomically increments the reel count for [packageName] on [date] (defaults to today),
     * updates session counters, and triggers the Dynamic Island pulse/glitch animation.
     */
    suspend fun recordReelSwipe(
        packageName: String,
        date: Long = LocalDate.now().toEpochDay(),
        delta: Int = 1
    ): DailyReelStatsEntity {
        val updated = dao.incrementReelCount(
            packageName = packageName,
            date = date,
            delta = delta
        )
        val nowMs = System.currentTimeMillis()
        val monitoredApp = MonitoredApp.fromPackage(packageName)

        _liveTelemetry.update { state ->
            state.copy(
                activePackage = packageName,
                activeApp = monitoredApp ?: state.activeApp,
                sessionInstagramCount = state.sessionInstagramCount +
                    if (monitoredApp == MonitoredApp.INSTAGRAM) delta else 0,
                sessionYoutubeCount = state.sessionYoutubeCount +
                    if (monitoredApp == MonitoredApp.YOUTUBE) delta else 0,
                todayTotalReels = updated.totalReels,
                brainState = updated.brainState,
                lastIncrementTimestampMs = nowMs
            )
        }
        return updated
    }

    /**
     * Called when an active Instagram Reel or YouTube Short viewport is detected on screen.
     */
    fun onReelOrShortScreenEntered(packageName: String) {
        val app = MonitoredApp.fromPackage(packageName)
        _liveTelemetry.update { state ->
            state.copy(
                activePackage = packageName,
                activeApp = app,
                isOverlayVisible = true
            )
        }
    }

    /**
     * Called as soon as the user navigates to the YouTube/Instagram Home feed, other tabs,
     * or exits to the Android Home screen / another app.
     */
    fun onReelOrShortScreenExited() {
        _liveTelemetry.update { state ->
            if (!state.isOverlayVisible && state.activeApp == null) {
                state
            } else {
                state.copy(
                    activePackage = null,
                    activeApp = null,
                    isOverlayVisible = false
                )
            }
        }
    }

    fun setAccessibilityServiceConnected(connected: Boolean) {
        _liveTelemetry.update {
            it.copy(
                isAccessibilityServiceConnected = connected,
                isOverlayVisible = if (!connected) false else it.isOverlayVisible
            )
        }
    }

    suspend fun pruneOlderThan28Days(today: LocalDate = LocalDate.now()): Int {
        val cutoffEpoch = today.minusDays(27).toEpochDay()
        return dao.purgeAllOlderThan(cutoffEpoch)
    }

    companion object {
        @Volatile
        private var INSTANCE: ReelRepository? = null

        fun getInstance(dao: ReelStatsDao): ReelRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReelRepository(dao).also { INSTANCE = it }
            }
        }
    }
}
