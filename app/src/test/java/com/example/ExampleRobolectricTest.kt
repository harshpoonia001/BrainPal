package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BrainPalDatabase
import com.example.data.BrainState
import com.example.data.MonitoredApp
import com.example.service.ReelTrackerAccessibilityService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: BrainPalDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BrainPalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read BrainPal app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BrainPal", appName)
    }

    @Test
    fun `verify 1_5 second debounce window in ReelTrackerAccessibilityService`() {
        assertTrue(ReelTrackerAccessibilityService.shouldAcceptSwipeAfterDebounce(0L, 1000L))
        assertFalse(ReelTrackerAccessibilityService.shouldAcceptSwipeAfterDebounce(1000L, 2200L))
        assertTrue(ReelTrackerAccessibilityService.shouldAcceptSwipeAfterDebounce(1000L, 2500L))
    }

    @Test
    fun `verify monitored apps scope excludes facebook`() {
        assertEquals(2, MonitoredApp.entries.size)
        assertTrue(MonitoredApp.MONITORED_PACKAGES.contains("com.instagram.android"))
        assertTrue(MonitoredApp.MONITORED_PACKAGES.contains("com.google.android.youtube"))
        assertFalse(MonitoredApp.MONITORED_PACKAGES.contains("com.facebook.katana"))
    }

    @Test
    fun `verify atomic incrementReelCount brain state degradation and 28-day pruning`() = runTest {
        val dao = database.reelStatsDao()
        val todayEpoch = LocalDate.of(2026, 10, 18).toEpochDay()
        val expiredEpoch = LocalDate.of(2026, 9, 10).toEpochDay()

        // Insert an old record (>28 days ago)
        dao.incrementReelCount(MonitoredApp.INSTAGRAM.packageName, expiredEpoch, delta = 12)
        assertNotNull(dao.getDayStats(expiredEpoch))

        // 0..30 = HEALTHY
        val s1 = dao.incrementReelCount(MonitoredApp.INSTAGRAM.packageName, todayEpoch, delta = 20)
        assertEquals(20, s1.totalReels)
        assertEquals(BrainState.HEALTHY, s1.brainState)

        // 31..75 = TIRED
        val s2 = dao.incrementReelCount(MonitoredApp.YOUTUBE.packageName, todayEpoch, delta = 30)
        assertEquals(50, s2.totalReels)
        assertEquals(BrainState.TIRED, s2.brainState)

        // 76..150 = DRAINED
        val s3 = dao.incrementReelCount(MonitoredApp.YOUTUBE.packageName, todayEpoch, delta = 50)
        assertEquals(100, s3.totalReels)
        assertEquals(BrainState.DRAINED, s3.brainState)

        // 150+ = BRAIN_ROT
        val s4 = dao.incrementReelCount(MonitoredApp.INSTAGRAM.packageName, todayEpoch, delta = 60)
        assertEquals(160, s4.totalReels)
        assertEquals(80, s4.instagramCount)
        assertEquals(80, s4.youtubeCount)
        assertEquals(BrainState.BRAIN_ROT, s4.brainState)

        // Prune records older than 28 days
        val cutoffEpoch = todayEpoch - 27L
        val deletedRows = dao.purgeAllOlderThan(cutoffEpoch)
        assertEquals(1, deletedRows)
        assertNull(dao.getDayStats(expiredEpoch))
        assertNotNull(dao.getDayStats(todayEpoch))
    }
}
