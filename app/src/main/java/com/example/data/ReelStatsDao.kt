package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

@Dao
interface ReelStatsDao {

    @Query("SELECT * FROM daily_reel_stats WHERE date >= :minEpochDay ORDER BY date ASC")
    fun observeStatsSince(minEpochDay: Long): Flow<List<DailyReelStatsEntity>>

    @Query("SELECT * FROM daily_reel_stats WHERE date = :epochDay LIMIT 1")
    fun observeDayStats(epochDay: Long): Flow<DailyReelStatsEntity?>

    @Query("SELECT * FROM daily_reel_stats WHERE date = :epochDay LIMIT 1")
    suspend fun getDayStats(epochDay: Long): DailyReelStatsEntity?

    @Query("SELECT * FROM daily_reel_stats WHERE date BETWEEN :startEpochDay AND :endEpochDay ORDER BY date ASC")
    suspend fun getStatsInRange(startEpochDay: Long, endEpochDay: Long): List<DailyReelStatsEntity>

    @Query("SELECT * FROM weekly_reports ORDER BY start_date DESC LIMIT 4")
    fun observeWeeklyReports(): Flow<List<WeeklyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(entity: DailyReelStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeeklyReport(report: WeeklyReportEntity)

    /**
     * Atomic SQLite upsert for real-time background writes without race conditions.
     */
    @Query(
        """
        INSERT INTO daily_reel_stats (
            date, iso_date, instagram_count, youtube_count, total_reels, brain_state
        ) VALUES (
            :date, :isoDate, :igDelta, :ytDelta, :totalDelta, :initialBrainState
        )
        ON CONFLICT(date) DO UPDATE SET
            instagram_count = instagram_count + :igDelta,
            youtube_count = youtube_count + :ytDelta,
            total_reels = total_reels + :totalDelta,
            brain_state = CASE
                WHEN (total_reels + :totalDelta) <= 30 THEN 'HEALTHY'
                WHEN (total_reels + :totalDelta) <= 75 THEN 'TIRED'
                WHEN (total_reels + :totalDelta) <= 150 THEN 'DRAINED'
                ELSE 'BRAIN_ROT'
            END
        """
    )
    suspend fun atomicUpsertReelDelta(
        date: Long,
        isoDate: String,
        igDelta: Int,
        ytDelta: Int,
        totalDelta: Int,
        initialBrainState: String
    )

    /**
     * Atomic upsert method: incrementReelCount(packageName, date)
     * Handles real-time background writes for Instagram Reels and YouTube Shorts.
     */
    @Transaction
    suspend fun incrementReelCount(
        packageName: String,
        date: Long = LocalDate.now().toEpochDay(),
        delta: Int = 1
    ): DailyReelStatsEntity {
        val safeDelta = delta.coerceAtLeast(1)
        val igDelta = if (packageName == MonitoredApp.INSTAGRAM.packageName) safeDelta else 0
        val ytDelta = if (packageName == MonitoredApp.YOUTUBE.packageName) safeDelta else 0
        val actualTotalDelta = igDelta + ytDelta
        if (actualTotalDelta == 0) {
            return getDayStats(date) ?: DailyReelStatsEntity(
                date = date,
                isoDate = LocalDate.ofEpochDay(date).toString()
            )
        }

        val localDate = LocalDate.ofEpochDay(date)
        val initialState = BrainState.fromCount(actualTotalDelta).name

        atomicUpsertReelDelta(
            date = date,
            isoDate = localDate.toString(),
            igDelta = igDelta,
            ytDelta = ytDelta,
            totalDelta = actualTotalDelta,
            initialBrainState = initialState
        )

        recalculateWeeklyReportForDate(localDate)
        return getDayStats(date)!!
    }

    @Transaction
    suspend fun recalculateWeeklyReportForDate(date: LocalDate) {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val startEpoch = monday.toEpochDay()
        val endEpoch = sunday.toEpochDay()

        val weekStats = getStatsInRange(startEpoch, endEpoch)
        val totalWeekReels = weekStats.sumOf { it.totalReels }
        val dailyAvg = totalWeekReels.toFloat() / 7f
        val peakEntity = weekStats.maxByOrNull { it.totalReels }
        val peakDayName = if (peakEntity != null && peakEntity.totalReels > 0) {
            LocalDate.ofEpochDay(peakEntity.date)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.US)
        } else {
            "None"
        }

        val weekFields = WeekFields.ISO
        val weekYear = monday.get(weekFields.weekBasedYear())
        val weekNumber = monday.get(weekFields.weekOfWeekBasedYear())
        val weekId = String.format(Locale.US, "%04d-W%02d", weekYear, weekNumber)

        upsertWeeklyReport(
            WeeklyReportEntity(
                weekId = weekId,
                startDate = startEpoch,
                endDate = endEpoch,
                totalReels = totalWeekReels,
                dailyAverage = dailyAvg,
                peakDay = peakDayName
            )
        )
    }

    /**
     * Automatic Retention Policy: discards records older than 28 days.
     */
    @Query("DELETE FROM daily_reel_stats WHERE date < :cutoffEpoch")
    suspend fun purgeOlderThan(cutoffEpoch: Long): Int

    @Query("DELETE FROM weekly_reports WHERE end_date < :cutoffEpoch")
    suspend fun purgeWeeklyReportsOlderThan(cutoffEpoch: Long): Int

    @Transaction
    suspend fun purgeAllOlderThan(cutoffEpoch: Long): Int {
        val deletedDays = purgeOlderThan(cutoffEpoch)
        purgeWeeklyReportsOlderThan(cutoffEpoch)
        return deletedDays
    }

    @Query("DELETE FROM daily_reel_stats")
    suspend fun clearAllDailyStats()

    @Query("DELETE FROM weekly_reports")
    suspend fun clearAllWeeklyReports()

    @Transaction
    suspend fun clearAllTelemetry() {
        clearAllDailyStats()
        clearAllWeeklyReports()
    }
}
