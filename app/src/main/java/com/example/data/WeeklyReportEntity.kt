package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "week_id")
    val weekId: String, // e.g., "2026-W42"
    @ColumnInfo(name = "start_date")
    val startDate: Long, // Monday epoch day
    @ColumnInfo(name = "end_date")
    val endDate: Long, // Sunday epoch day
    @ColumnInfo(name = "total_reels")
    val totalReels: Int = 0,
    @ColumnInfo(name = "daily_average")
    val dailyAverage: Float = 0f,
    @ColumnInfo(name = "peak_day")
    val peakDay: String = "None"
)
