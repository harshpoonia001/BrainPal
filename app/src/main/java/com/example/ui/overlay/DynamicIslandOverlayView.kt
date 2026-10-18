package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrainState
import com.example.data.MonitoredApp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Module 2: "Dynamic Island" Capsule Overlay Composable
 * Renders the top-center pinned capsule displaying the deteriorating brain avatar,
 * live reel counter, and state-driven animations:
 * - 0..30: Healthy Brain (neutral dark-gray/black pill, soft white text)
 * - 31..75: Tired Brain (subtle amber accent)
 * - 76..150: Drained Brain (warning orange accent, slight scale pulse)
 * - 150+: Brain Rot (alarming crimson background, shaking/glitch animation on increment)
 */
@Composable
fun DynamicIslandOverlayView(
    totalReels: Int,
    brainState: BrainState,
    activeApp: MonitoredApp?,
    lastIncrementTimestampMs: Long,
    modifier: Modifier = Modifier,
    showExtendedStatus: Boolean = true
) {
    var isRecentlyIncremented by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val incrementScale = remember { Animatable(1f) }

    LaunchedEffect(lastIncrementTimestampMs, totalReels) {
        if (lastIncrementTimestampMs > 0L) {
            isRecentlyIncremented = true
            // Pop scale on every reel increment
            incrementScale.snapTo(1.12f)
            if (brainState == BrainState.BRAIN_ROT) {
                // High-frequency horizontal shake/glitch for Brain Rot (150+ reels)
                val offsets = listOf(-10f, 10f, -8f, 8f, -5f, 5f, -2f, 2f, 0f)
                for (dx in offsets) {
                    shakeOffset.animateTo(dx, animationSpec = tween(32))
                }
            }
            incrementScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(1100L)
            isRecentlyIncremented = false
        }
    }

    // Continuous subtle pulse for DRAINED (76-150) and BRAIN_ROT (150+)
    val infiniteTransition = rememberInfiniteTransition(label = "capsule_pulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = when (brainState) {
            BrainState.DRAINED -> 1.045f
            BrainState.BRAIN_ROT -> 1.06f
            else -> 1.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_scale_pulse"
    )

    val glitchScanlineShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450),
            repeatMode = RepeatMode.Restart
        ),
        label = "glitch_scanline"
    )

    val pillBackgroundColor by animateColorAsState(
        targetValue = when (brainState) {
            BrainState.HEALTHY -> Color(0xFF0B0E14)
            BrainState.TIRED -> Color(0xFF1C160C)
            BrainState.DRAINED -> Color(0xFF23120B)
            BrainState.BRAIN_ROT -> Color(0xFF7F1D1D)
        },
        animationSpec = tween(350),
        label = "pill_bg_color"
    )

    val borderAccentColor by animateColorAsState(
        targetValue = when (brainState) {
            BrainState.HEALTHY -> Color(0xFF334155)
            BrainState.TIRED -> Color(0xFFF59E0B)
            BrainState.DRAINED -> Color(0xFFF97316)
            BrainState.BRAIN_ROT -> Color(0xFFEF4444)
        },
        animationSpec = tween(350),
        label = "pill_border_color"
    )

    val counterTextColor by animateColorAsState(
        targetValue = when (brainState) {
            BrainState.HEALTHY -> Color(0xFFF8FAFC)
            BrainState.TIRED -> Color(0xFFFDE68A)
            BrainState.DRAINED -> Color(0xFFFED7AA)
            BrainState.BRAIN_ROT -> Color(0xFFFFE4E6)
        },
        animationSpec = tween(350),
        label = "pill_text_color"
    )

    val dotIndicatorColor = when (brainState) {
        BrainState.HEALTHY -> Color(0xFF10B981)
        BrainState.TIRED -> Color(0xFFF59E0B)
        BrainState.DRAINED -> Color(0xFFF97316)
        BrainState.BRAIN_ROT -> Color(0xFFFF2E4C)
    }

    val combinedScale = ambientPulse * incrementScale.value

    Box(
        modifier = modifier
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .scale(combinedScale)
            .shadow(
                elevation = if (brainState == BrainState.BRAIN_ROT) 14.dp else 8.dp,
                shape = RoundedCornerShape(50),
                ambientColor = borderAccentColor,
                spotColor = borderAccentColor
            )
            .clip(RoundedCornerShape(50))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        pillBackgroundColor,
                        pillBackgroundColor.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = if (brainState == BrainState.HEALTHY) 1.dp else 1.5.dp,
                color = borderAccentColor.copy(alpha = if (isRecentlyIncremented) 1f else 0.75f),
                shape = RoundedCornerShape(50)
            )
            .drawWithContent {
                drawContent()
                // Glitch scanline overlay when in BRAIN_ROT state
                if (brainState == BrainState.BRAIN_ROT) {
                    val yPos = size.height * glitchScanlineShift
                    drawLine(
                        color = Color(0x55FF6B81),
                        start = Offset(0f, yPos),
                        end = Offset(size.width, yPos),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("dynamic_island_capsule"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Live telemetry indicator dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotIndicatorColor)
            )

            Spacer(modifier = Modifier.width(7.dp))

            // Expressive deteriorating brain avatar
            Text(
                text = brainState.emoji,
                fontSize = 16.sp,
                modifier = Modifier.testTag("dynamic_island_emoji")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Live counter
            Text(
                text = totalReels.toString(),
                color = counterTextColor,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.testTag("dynamic_island_counter")
            )

            // Expandable status / active app badge on increment or when showExtendedStatus is true
            AnimatedVisibility(
                visible = showExtendedStatus || isRecentlyIncremented || brainState == BrainState.BRAIN_ROT,
                enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                exit = fadeOut(tween(180)) + scaleOut(tween(180))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(borderAccentColor.copy(alpha = 0.22f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when {
                                isRecentlyIncremented && activeApp != null -> "+1 ${activeApp.shortLabel}"
                                brainState == BrainState.BRAIN_ROT -> "ROT ALERT"
                                else -> brainState.label.uppercase()
                            },
                            color = counterTextColor,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }
        }
    }
}
