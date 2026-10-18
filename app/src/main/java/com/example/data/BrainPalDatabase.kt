package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DailyReelStatsEntity::class, WeeklyReportEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(BrainStateConverters::class)
abstract class BrainPalDatabase : RoomDatabase() {
    abstract fun reelStatsDao(): ReelStatsDao

    companion object {
        private const val DATABASE_NAME = "brainpal_rolling_28d.db"

        @Volatile
        private var INSTANCE: BrainPalDatabase? = null

        fun getInstance(context: Context): BrainPalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrainPalDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
