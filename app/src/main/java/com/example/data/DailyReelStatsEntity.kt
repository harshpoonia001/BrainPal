package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reel_stats")
data class DailyReelStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: Long, // Epoch day (LocalDate.toEpochDay())
    @ColumnInfo(name = "iso_date")
    val isoDate: String, // YYYY-MM-DD format
    @ColumnInfo(name = "instagram_count")
    val instagramCount: Int = 0,
    @ColumnInfo(name = "youtube_count")
    val youtubeCount: Int = 0,
    @ColumnInfo(name = "total_reels")
    val totalReels: Int = 0,
    @ColumnInfo(name = "brain_state")
    val brainState: BrainState = BrainState.HEALTHY
)
