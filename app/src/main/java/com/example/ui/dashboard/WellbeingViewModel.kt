package com.example.ui.dashboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BrainState
import com.example.data.DailyReelStatsEntity
import com.example.data.LiveSessionTelemetry
import com.example.data.MonitoredApp
import com.example.data.ReelRepository
import com.example.service.FloatingOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

data class DayBarMetric(
    val date: LocalDate,
    val epochDay: Long,
    val dayShortLabel: String, // "Mon", "Tue", ...
    val formattedDate: String, // "Oct 18"
    val instagramCount: Int,
    val youtubeCount: Int,
    val totalReels: Int,
    val brainState: BrainState,
    val exceedsDailyLimit: Boolean
)

data class AppBreakdownItem(
    val app: MonitoredApp,
    val reelCount: Int,
    val percentageShare: Float, // 0f..1f
    val percentageInt: Int // 0..100
)

data class WeekPageData(
    val weekOffset: Int, // 0 = This Week, 1 = Last Week, 2 = 2 Weeks Ago, 3 = 3 Weeks Ago
    val title: String,
    val shortTabTitle: String,
    val weekId: String,
    val dateRangeLabel: String,
    val days: List<DayBarMetric>,
    val weekTotalReels: Int,
    val dailyAverage: Float,
    val deltaPercentageVsPriorWeek: Int?,
    val weekBrainState: BrainState,
    val peakDayLabel: String
)

data class WellbeingDashboardUiState(
    val weeks: List<WeekPageData> = emptyList(), // Always 4 weeks: index 0..3
    val selectedWeekIndex: Int = 0,
    val selectedDayEpoch: Long? = null,
    val liveTelemetry: LiveSessionTelemetry = LiveSessionTelemetry(),
    val hasOverlayPermission: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isDarkTheme: Boolean = false,
    val dailyLimitThreshold: Int = 100
) {
    val currentWeek: WeekPageData?
        get() = weeks.getOrNull(selectedWeekIndex)

    val selectedDayMetric: DayBarMetric?
        get() = currentWeek?.days?.firstOrNull { it.epochDay == selectedDayEpoch }

    val activeBreakdownItems: List<AppBreakdownItem>
        get() {
            val ig: Int
            val yt: Int
            val day = selectedDayMetric
            if (day != null) {
                ig = day.instagramCount
                yt = day.youtubeCount
            } else {
                val week = currentWeek
                ig = week?.days?.sumOf { it.instagramCount } ?: 0
                yt = week?.days?.sumOf { it.youtubeCount } ?: 0
            }
            val total = (ig + yt).coerceAtLeast(1)
            val hasAny = (ig + yt) > 0
            return listOf(
                AppBreakdownItem(
                    app = MonitoredApp.INSTAGRAM,
                    reelCount = ig,
                    percentageShare = if (hasAny) ig.toFloat() / total else 0f,
                    percentageInt = if (hasAny) ((ig * 100f) / total).roundToInt() else 0
                ),
                AppBreakdownItem(
                    app = MonitoredApp.YOUTUBE,
                    reelCount = yt,
                    percentageShare = if (hasAny) yt.toFloat() / total else 0f,
                    percentageInt = if (hasAny) ((yt * 100f) / total).roundToInt() else 0
                )
            )
        }
}

class WellbeingViewModel(
    private val repository: ReelRepository
) : ViewModel() {

    private val selectedWeekIndexFlow = MutableStateFlow(0)
    private val selectedDayEpochFlow = MutableStateFlow<Long?>(null)
    private val permissionStateFlow = MutableStateFlow(Pair(false, false))
    private val isDarkThemeFlow = MutableStateFlow(false)
    private var prefsLoaded = false

    val uiState: StateFlow<WellbeingDashboardUiState> = combine(
        repository.observeRolling28DayStats(),
        repository.liveTelemetry,
        selectedWeekIndexFlow,
        selectedDayEpochFlow,
        combine(permissionStateFlow, isDarkThemeFlow) { perms, isDark -> Pair(perms, isDark) }
    ) { dbStats, telemetry, weekIdx, dayEpoch, permsAndTheme ->
        val permissions = permsAndTheme.first
        val isDark = permsAndTheme.second
        val safeWeekIdx = weekIdx.coerceIn(0, 3)
        val weeksList = buildFourWeeksData(
            dbStats = dbStats,
            today = LocalDate.now(),
            dailyLimit = telemetry.dailyGoalLimit
        )
        val activeWeek = weeksList.getOrNull(safeWeekIdx)
        val validDayEpoch = if (activeWeek?.days?.any { it.epochDay == dayEpoch } == true) {
            dayEpoch
        } else {
            null
        }

        WellbeingDashboardUiState(
            weeks = weeksList,
            selectedWeekIndex = safeWeekIdx,
            selectedDayEpoch = validDayEpoch,
            liveTelemetry = telemetry,
            hasOverlayPermission = permissions.first,
            isAccessibilityEnabled = permissions.second || telemetry.isAccessibilityServiceConnected,
            isDarkTheme = isDark,
            dailyLimitThreshold = telemetry.dailyGoalLimit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WellbeingDashboardUiState(
            weeks = buildFourWeeksData(emptyList(), LocalDate.now(), 100),
            selectedDayEpoch = null
        )
    )

    fun selectWeek(weekIndex: Int) {
        val clamped = weekIndex.coerceIn(0, 3)
        selectedWeekIndexFlow.value = clamped
        selectedDayEpochFlow.value = null
    }

    fun selectDay(epochDay: Long?) {
        selectedDayEpochFlow.value = if (selectedDayEpochFlow.value == epochDay) {
            null
        } else {
            epochDay
        }
    }

    fun toggleTheme(context: Context) {
        val nextDark = !isDarkThemeFlow.value
        isDarkThemeFlow.value = nextDark
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_THEME, nextDark)
            .apply()
    }

    fun refreshSystemPermissionsAndEnsureOverlayHiddenOnDashboard(context: Context) {
        if (!prefsLoaded) {
            prefsLoaded = true
            val savedDark = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_THEME, false)
            isDarkThemeFlow.value = savedDark
        }

        repository.onReelOrShortScreenExited()
        FloatingOverlayService.hideOverlay(context)

        val canDraw = Settings.canDrawOverlays(context)
        val a11yEnabled = isAccessibilityServiceEnabled(context)
        permissionStateFlow.value = Pair(canDraw, a11yEnabled)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }

    companion object {
        private const val PREFS_NAME = "brainpal_ui_prefs"
        private const val KEY_DARK_THEME = "supabase_dark_theme_enabled"

        private val WEEK_TITLES = listOf(
            "This Week",
            "Last Week",
            "2 Weeks Ago",
            "3 Weeks Ago"
        )

        private val SHORT_TAB_TITLES = listOf(
            "This Week",
            "Last Week",
            "2W Ago",
            "3W Ago"
        )

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        fun buildFourWeeksData(
            dbStats: List<DailyReelStatsEntity>,
            today: LocalDate,
            dailyLimit: Int
        ): List<WeekPageData> {
            val statsByEpoch = dbStats.associateBy { it.date }
            val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekFields = WeekFields.ISO

            val rawWeekTotals = (0..3).map { offset ->
                val monday = currentMonday.minusWeeks(offset.toLong())
                (0..6).sumOf { dayIdx ->
                    val date = monday.plusDays(dayIdx.toLong())
                    statsByEpoch[date.toEpochDay()]?.totalReels ?: 0
                }
            }

            return (0..3).map { offset ->
                val monday = currentMonday.minusWeeks(offset.toLong())
                val sunday = monday.plusDays(6)

                val days = (0..6).map { dayIdx ->
                    val date = monday.plusDays(dayIdx.toLong())
                    val epoch = date.toEpochDay()
                    val entity = statsByEpoch[epoch]
                    val total = entity?.totalReels ?: 0
                    DayBarMetric(
                        date = date,
                        epochDay = epoch,
                        dayShortLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                        formattedDate = date.format(DATE_FORMATTER),
                        instagramCount = entity?.instagramCount ?: 0,
                        youtubeCount = entity?.youtubeCount ?: 0,
                        totalReels = total,
                        brainState = entity?.brainState ?: BrainState.fromCount(total),
                        exceedsDailyLimit = total > dailyLimit
                    )
                }

                val weekTotal = rawWeekTotals[offset]
                val elapsedDaysInWeek = if (offset == 0) {
                    (today.toEpochDay() - monday.toEpochDay() + 1).coerceIn(1L, 7L).toInt()
                } else {
                    7
                }
                val dailyAvg = weekTotal.toFloat() / elapsedDaysInWeek.toFloat()

                val priorWeekTotal = rawWeekTotals.getOrNull(offset + 1)
                val deltaPct = if (priorWeekTotal != null && priorWeekTotal > 0) {
                    (((weekTotal - priorWeekTotal).toFloat() / priorWeekTotal.toFloat()) * 100f).roundToInt()
                } else {
                    null
                }

                val peakDayMetric = days.maxByOrNull { it.totalReels }
                val peakLabel = if (peakDayMetric != null && peakDayMetric.totalReels > 0) {
                    "${peakDayMetric.dayShortLabel} · ${peakDayMetric.totalReels}"
                } else {
                    "None"
                }

                val weekYear = monday.get(weekFields.weekBasedYear())
                val weekNumber = monday.get(weekFields.weekOfWeekBasedYear())
                val weekId = String.format(Locale.US, "%04d-W%02d", weekYear, weekNumber)

                WeekPageData(
                    weekOffset = offset,
                    title = WEEK_TITLES[offset],
                    shortTabTitle = SHORT_TAB_TITLES[offset],
                    weekId = weekId,
                    dateRangeLabel = "${monday.format(DATE_FORMATTER)} – ${sunday.format(DATE_FORMATTER)}",
                    days = days,
                    weekTotalReels = weekTotal,
                    dailyAverage = dailyAvg,
                    deltaPercentageVsPriorWeek = deltaPct,
                    weekBrainState = BrainState.fromCount(dailyAvg.roundToInt()),
                    peakDayLabel = peakLabel
                )
            }
        }

        fun provideFactory(repository: ReelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WellbeingViewModel(repository) as T
                }
            }
    }
}
