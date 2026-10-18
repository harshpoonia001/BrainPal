package com.example.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BrainState
import com.example.data.MonitoredApp
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.LocalBrainPalPalette
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.YoutubeAccent
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun WellbeingDashboardScreen(
    viewModel: WellbeingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalBrainPalPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemPermissionsAndEnsureOverlayHiddenOnDashboard(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedWeekIndex,
        pageCount = { 4 }
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.selectedWeekIndex) {
            viewModel.selectWeek(pagerState.currentPage)
        }
    }

    LaunchedEffect(uiState.selectedWeekIndex) {
        if (pagerState.currentPage != uiState.selectedWeekIndex) {
            pagerState.animateScrollToPage(uiState.selectedWeekIndex)
        }
    }

    val animatedCanvasBg by animateColorAsState(
        targetValue = palette.canvasBackground,
        animationSpec = tween(320),
        label = "canvas_bg_color"
    )

    // Minimalist ambient mesh & subtle architectural grid backdrop (Apple Light / Supabase Dark)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedCanvasBg)
            .drawBehind {
                // Subtle architectural grid lines in top hero area (signature Supabase dark / clean light texture)
                val gridStep = 36.dp.toPx()
                val maxGridY = size.height * 0.42f
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = palette.gridLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, maxGridY),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y <= maxGridY) {
                    drawLine(
                        color = palette.gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }

                // Primary top-left ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.ambientGlowPrimary,
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.06f),
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = Offset(size.width * 0.16f, size.height * 0.06f)
                )
                // Secondary top-right ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.ambientGlowSecondary,
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.24f),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.88f, size.height * 0.24f)
                )
            }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("wellbeing_dashboard_list"),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 16.dp,
                        bottom = 36.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. Minimalist Header with Theme Switcher (Apple Light <-> Supabase Dark) & Guard Status
                    item {
                        DashboardTopBar(
                            isGuardActive = uiState.isAccessibilityEnabled && uiState.hasOverlayPermission,
                            isDarkTheme = uiState.isDarkTheme,
                            onToggleTheme = { viewModel.toggleTheme(context) }
                        )
                    }

                    // 2. 4-Week Segmented Glass Control & Date Carousel
                    item {
                        FourWeekSegmentedHeader(
                            weeks = uiState.weeks,
                            selectedWeekIndex = uiState.selectedWeekIndex,
                            onSelectWeek = { targetIdx ->
                                coroutineScope.launch {
                                    viewModel.selectWeek(targetIdx)
                                }
                            }
                        )
                    }

                    // 3. 4-Week Pager: Summary Metric + 7-Day Interactive Bar Chart inside a Unified Frosted Glass Card
                    item {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("four_week_horizontal_pager")
                        ) { pageIndex ->
                            val weekData = uiState.weeks.getOrNull(pageIndex)
                            if (weekData != null) {
                                UnifiedWeeklyGlassCard(
                                    weekData = weekData,
                                    selectedDay = if (pageIndex == uiState.selectedWeekIndex) {
                                        uiState.selectedDayMetric
                                    } else {
                                        null
                                    },
                                    selectedDayEpoch = if (pageIndex == uiState.selectedWeekIndex) {
                                        uiState.selectedDayEpoch
                                    } else {
                                        null
                                    },
                                    dailyLimit = uiState.dailyLimitThreshold,
                                    onSelectDay = { epochDay ->
                                        viewModel.selectDay(epochDay)
                                    },
                                    onClearDaySelection = {
                                        viewModel.selectDay(null)
                                    }
                                )
                            }
                        }
                    }

                    // 4. App Attribution Grouped Glass Card (Instagram Reels & YouTube Shorts)
                    item {
                        AppAttributionGlassSection(
                            items = uiState.activeBreakdownItems,
                            selectedDay = uiState.selectedDayMetric,
                            weekTitle = uiState.currentWeek?.title ?: "This Week"
                        )
                    }

                    // 5. Minimalist System Permissions & Guard Status Glass Card
                    item {
                        SystemProtectionGlassCard(
                            isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                            hasOverlayPermission = uiState.hasOverlayPermission,
                            onOpenAccessibilitySettings = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            onRequestOverlayPermission = {
                                runCatching {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Frosted Glass Card adapting seamlessly between:
 * - Light Mode: Translucent pearl-white glass with specular white rim
 * - Dark Mode: Supabase layered carbon-slate glass (`#1F1F1F` -> `#171717`) with directional 1dp specular border
 */
@Composable
private fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalBrainPalPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .shadow(
                elevation = if (palette.isDark) 12.dp else 8.dp,
                shape = shape,
                ambientColor = if (palette.isDark) Color(0x55000000) else Color(0x12000000),
                spotColor = if (palette.isDark) Color(0x66000000) else Color(0x14000000)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.cardGradientTop,
                        palette.cardGradientBottom
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.cardBorderTop,
                        palette.cardBorderBottom
                    )
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun DashboardTopBar(
    isGuardActive: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val palette = LocalBrainPalPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "WELLBEING",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = palette.textTertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "BrainPal",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = (-0.8).sp,
                color = palette.textPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Frosted Guard Status Pill
            val dotColor = if (isGuardActive) palette.statusHealthy else palette.statusTired
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.cardGradientTop)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(palette.cardBorderTop, palette.cardBorderBottom)
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGuardActive) "Active Guard" else "Setup Needed",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary
                )
            }

            // Theme Switcher Button (Apple Light <-> Supabase Dark)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.cardGradientTop)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(palette.cardBorderTop, palette.cardBorderBottom)
                        ),
                        shape = CircleShape
                    )
                    .clickable { onToggleTheme() }
                    .testTag("theme_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme",
                    tint = if (isDarkTheme) palette.primaryAccent else palette.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FourWeekSegmentedHeader(
    weeks: List<WeekPageData>,
    selectedWeekIndex: Int,
    onSelectWeek: (Int) -> Unit
) {
    val palette = LocalBrainPalPalette.current
    val currentWeek = weeks.getOrNull(selectedWeekIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("four_week_carousel_header"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Segmented Pill Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.segmentedTrackBg)
                .border(1.dp, palette.segmentedTrackBorder, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            weeks.forEachIndexed { index, week ->
                val isSelected = index == selectedWeekIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(11.dp),
                                    ambientColor = Color(0x22000000),
                                    spotColor = Color(0x22000000)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) palette.segmentedThumbBg else Color.Transparent)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = palette.segmentedThumbBorder,
                                    shape = RoundedCornerShape(11.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectWeek(index) }
                        .padding(horizontal = 4.dp)
                        .testTag("week_tab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = week.shortTabTitle,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) palette.textPrimary else palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Subtle Date Navigation Bar with Bounds Lock (0..3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val canGoOlder = selectedWeekIndex < 3
            IconButton(
                onClick = { if (canGoOlder) onSelectWeek(selectedWeekIndex + 1) },
                enabled = canGoOlder,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (canGoOlder) palette.cardGradientTop else Color.Transparent
                    )
                    .then(
                        if (canGoOlder) {
                            Modifier.border(1.dp, palette.cardBorderBottom, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .testTag("carousel_prev_week_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Week",
                    tint = if (canGoOlder) palette.textPrimary else palette.textTertiary.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "${currentWeek?.title ?: "This Week"} · ${currentWeek?.dateRangeLabel.orEmpty()}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = palette.textSecondary
            )

            val canGoNewer = selectedWeekIndex > 0
            IconButton(
                onClick = { if (canGoNewer) onSelectWeek(selectedWeekIndex - 1) },
                enabled = canGoNewer,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (canGoNewer) palette.cardGradientTop else Color.Transparent
                    )
                    .then(
                        if (canGoNewer) {
                            Modifier.border(1.dp, palette.cardBorderBottom, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .testTag("carousel_next_week_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Week",
                    tint = if (canGoNewer) palette.textPrimary else palette.textTertiary.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun UnifiedWeeklyGlassCard(
    weekData: WeekPageData,
    selectedDay: DayBarMetric?,
    selectedDayEpoch: Long?,
    dailyLimit: Int,
    onSelectDay: (Long) -> Unit,
    onClearDaySelection: () -> Unit
) {
    val palette = LocalBrainPalPalette.current
    val displayedTotal = selectedDay?.totalReels ?: weekData.weekTotalReels
    val displayedBrainState = selectedDay?.brainState ?: weekData.weekBrainState
    val stateAccentColor = when (displayedBrainState) {
        BrainState.HEALTHY -> palette.statusHealthy
        BrainState.TIRED -> palette.statusTired
        BrainState.DRAINED -> palette.statusDrained
        BrainState.BRAIN_ROT -> palette.statusRot
    }

    val maxDayReels = remember(weekData.days, dailyLimit) {
        val peak = weekData.days.maxOfOrNull { it.totalReels } ?: 0
        maxOf(peak, dailyLimit + 25, 120)
    }

    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_summary_card")
    ) {
        // Top Header: Context Label + Brain Health Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedDay != null) {
                    "${selectedDay.dayShortLabel.uppercase()}, ${selectedDay.formattedDate.uppercase()}"
                } else {
                    "WEEKLY TOTAL"
                },
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = palette.textTertiary
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(stateAccentColor.copy(alpha = 0.14f))
                    .border(
                        width = 0.8.dp,
                        color = stateAccentColor.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("brain_health_evaluation_chip"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = displayedBrainState.emoji, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = displayedBrainState.label,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = stateAccentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Large Bold Numeric Headline + Clear Selection Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%,d", displayedTotal),
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-1.2).sp,
                    color = palette.textPrimary,
                    modifier = Modifier.testTag("summary_total_reels_value")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "reels",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }

            if (selectedDay != null) {
                Text(
                    text = "Show Week",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.primaryAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearDaySelection() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("show_full_week_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Daily Average & Percentage Delta vs Prior Week
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "Daily Average: %.1f reels/day",
                    weekData.dailyAverage
                ),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = palette.textSecondary,
                modifier = Modifier.testTag("daily_average_text")
            )

            val delta = weekData.deltaPercentageVsPriorWeek
            if (delta != null) {
                val deltaColor = when {
                    delta > 0 -> palette.statusRot
                    delta < 0 -> palette.statusHealthy
                    else -> palette.textTertiary
                }
                val deltaText = when {
                    delta > 0 -> "▲ ${abs(delta)}% vs last week"
                    delta < 0 -> "▼ ${abs(delta)}% vs last week"
                    else -> "0% vs last week"
                }
                Text(
                    text = deltaText,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = deltaColor,
                    modifier = Modifier.testTag("weekly_delta_badge")
                )
            } else {
                Text(
                    text = "Peak: ${weekData.peakDayLabel}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.textTertiary,
                    modifier = Modifier.testTag("weekly_delta_badge")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7-Day Interactive Bar Chart
        val animatedFractions = weekData.days.map { day ->
            val targetFraction = (day.totalReels.toFloat() / maxDayReels.toFloat()).coerceIn(0f, 1f)
            animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "bar_height_${day.dayShortLabel}"
            ).value
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag("seven_day_bar_chart_card"),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("seven_day_bar_canvas")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(weekData.days) {
                            detectTapGestures { tapOffset ->
                                if (size.width > 0 && weekData.days.isNotEmpty()) {
                                    val slotWidth = size.width.toFloat() / weekData.days.size
                                    val tappedIdx = (tapOffset.x / slotWidth)
                                        .toInt()
                                        .coerceIn(0, weekData.days.lastIndex)
                                    onSelectDay(weekData.days[tappedIdx].epochDay)
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val slotWidth = canvasWidth / 7f
                    val barWidth = (slotWidth * 0.42f).coerceAtMost(28.dp.toPx())

                    // Subtle horizontal hairlines
                    listOf(0f, 0.5f, 1f).forEach { ratio ->
                        val y = canvasHeight * ratio
                        drawLine(
                            color = palette.hairlineDivider,
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Dashed 100-Reel Daily Limit Line
                    val limitRatio = (dailyLimit.toFloat() / maxDayReels.toFloat()).coerceIn(0f, 1f)
                    val limitY = canvasHeight * (1f - limitRatio)
                    drawLine(
                        color = palette.statusRot.copy(alpha = 0.5f),
                        start = Offset(0f, limitY),
                        end = Offset(canvasWidth, limitY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Draw 7 Bars (Mon..Sun)
                    weekData.days.forEachIndexed { index, day ->
                        val isSelected = day.epochDay == selectedDayEpoch
                        val hasSelection = selectedDayEpoch != null
                        val centerX = slotWidth * index + slotWidth / 2f
                        val barLeft = centerX - barWidth / 2f

                        // Recessed capsule track
                        drawRoundRect(
                            color = palette.barRecessedTrack,
                            topLeft = Offset(barLeft, 0f),
                            size = Size(barWidth, canvasHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        val barColor = when {
                            day.exceedsDailyLimit && (!hasSelection || isSelected) -> palette.statusRot
                            day.exceedsDailyLimit -> palette.statusRot.copy(alpha = 0.45f)
                            isSelected -> palette.normalBarActive
                            !hasSelection -> palette.normalBarDefault
                            else -> palette.normalBarMuted
                        }

                        val fraction = animatedFractions[index]
                        val minVisibleHeight = if (day.totalReels > 0) 6.dp.toPx() else 0f
                        val barHeight = (canvasHeight * fraction).coerceAtLeast(minVisibleHeight)
                        val barTop = canvasHeight - barHeight

                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right-side Y-axis scale labels
            Column(
                modifier = Modifier.height(160.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = maxDayReels.toString(),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 10.sp,
                    color = palette.textTertiary
                )
                Text(
                    text = (maxDayReels / 2).toString(),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 10.sp,
                    color = palette.textTertiary
                )
                Text(
                    text = "0",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 10.sp,
                    color = palette.textTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day Selector Labels Row (Mon..Sun)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekData.days.forEach { day ->
                val isSelected = day.epochDay == selectedDayEpoch
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) palette.primaryAccent.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .clickable { onSelectDay(day.epochDay) }
                        .testTag("bar_day_selector_${day.dayShortLabel.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day.dayShortLabel.take(3),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        color = when {
                            isSelected -> palette.primaryAccent
                            day.exceedsDailyLimit -> palette.statusRot
                            else -> palette.textSecondary
                        }
                    )
                    Text(
                        text = day.totalReels.toString(),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = if (isSelected) palette.textPrimary else palette.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppAttributionGlassSection(
    items: List<AppBreakdownItem>,
    selectedDay: DayBarMetric?,
    weekTitle: String
) {
    val palette = LocalBrainPalPalette.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOST USED",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = palette.textTertiary
            )
            Text(
                text = if (selectedDay != null) {
                    "${selectedDay.dayShortLabel}, ${selectedDay.formattedDate}"
                } else {
                    weekTitle
                },
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = palette.textSecondary
            )
        }

        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 18.dp)
        ) {
            items.forEachIndexed { index, item ->
                AppAttributionRow(item = item)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = palette.hairlineDivider,
                        modifier = Modifier.padding(start = 52.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppAttributionRow(
    item: AppBreakdownItem
) {
    val palette = LocalBrainPalPalette.current
    val brandColor = when (item.app) {
        MonitoredApp.INSTAGRAM -> InstagramAccent
        MonitoredApp.YOUTUBE -> YoutubeAccent
    }

    val iconVector = when (item.app) {
        MonitoredApp.INSTAGRAM -> Icons.Outlined.VideoLibrary
        MonitoredApp.YOUTUBE -> Icons.Outlined.PlayCircle
    }

    val animatedProgress by animateFloatAsState(
        targetValue = item.percentageShare.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "app_progress_${item.app.name}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("app_breakdown_card_${item.app.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    brandColor.copy(alpha = 0.18f),
                                    brandColor.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            color = brandColor.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = item.app.displayName,
                        tint = brandColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.app.displayName,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.barRecessedTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(brandColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.reelCount}",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.testTag("app_count_${item.app.name.lowercase()}")
                )
                Text(
                    text = "${item.percentageInt}%",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
            }
        }
    }
}

@Composable
private fun SystemProtectionGlassCard(
    isAccessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val palette = LocalBrainPalPalette.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SYSTEM PROTECTION",
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = palette.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("system_services_card"),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 18.dp)
        ) {
            CupertinoPermissionRow(
                icon = Icons.Outlined.Shield,
                title = "Reel & Shorts Detection",
                subtitle = if (isAccessibilityEnabled) {
                    "Monitoring Instagram Reels & YouTube Shorts"
                } else {
                    "Enable Accessibility Service to track vertical swipes"
                },
                isGranted = isAccessibilityEnabled,
                actionLabel = if (isAccessibilityEnabled) "Enabled" else "Enable",
                onActionClick = onOpenAccessibilitySettings,
                testTag = "accessibility_service_row"
            )

            HorizontalDivider(
                color = palette.hairlineDivider,
                modifier = Modifier.padding(start = 52.dp)
            )

            CupertinoPermissionRow(
                icon = Icons.Outlined.Layers,
                title = "Dynamic Island Capsule",
                subtitle = if (hasOverlayPermission) {
                    "Auto-appears only when Reels or Shorts are open"
                } else {
                    "Allow display over apps for live top-notch counter"
                },
                isGranted = hasOverlayPermission,
                actionLabel = if (hasOverlayPermission) "Allowed" else "Allow",
                onActionClick = onRequestOverlayPermission,
                testTag = "overlay_permission_row"
            )
        }

        Text(
            text = "On-device telemetry is automatically pruned after 28 days. Home feeds and personal messages are never read.",
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = palette.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CupertinoPermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit,
    testTag: String
) {
    val palette = LocalBrainPalPalette.current
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) palette.statusHealthy else palette.primaryAccent,
        label = "permission_status_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() }
            .padding(vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Outlined.CheckCircle else icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        val pillTextColor = when {
            isGranted -> palette.statusHealthy
            palette.isDark -> Color(0xFF062819)
            else -> Color.White
        }

        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isGranted) statusColor.copy(alpha = 0.14f) else palette.primaryAccent
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = pillTextColor
            )
        }
    }
}
