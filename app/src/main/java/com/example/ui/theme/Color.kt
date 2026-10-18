package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Apple-Inspired Minimalist Light & Frosted Glass Palette
val CupertinoCanvasBg = Color(0xFFF2F2F7)
val GlassSurfaceWhite = Color(0xDDF9FAFB)
val GlassSurfaceElevated = Color(0xF2FFFFFF)
val CupertinoHairline = Color(0xFFE5E5EA)
val CupertinoSegmentedTrack = Color(0xFFE5E5EA)

val InkPrimary = Color(0xFF1C1C1E)
val InkSecondary = Color(0xFF6C6C70)
val InkTertiary = Color(0xFF8E8E93)

val SystemBlue = Color(0xFF007AFF)
val SystemGreen = Color(0xFF34C759)
val SystemAmber = Color(0xFFFF9500)
val SystemOrange = Color(0xFFFF6B22)
val SystemRed = Color(0xFFFF3B30)

// Supabase-Inspired Minimalist Dark Glass Palette
val SupabaseCanvasBg = Color(0xFF121212)
val SupabaseSurfaceTop = Color(0xEB1F1F1F)
val SupabaseSurfaceBottom = Color(0xE6171717)
val SupabaseBorderTop = Color(0xFF363636)
val SupabaseBorderBottom = Color(0xFF242424)
val SupabaseHairline = Color(0xFF262626)
val SupabaseSegmentedTrack = Color(0xFF171717)
val SupabaseSegmentedThumb = Color(0xFF262626)
val SupabaseEmerald = Color(0xFF3ECF8E)
val SupabaseCrimson = Color(0xFFF43F5E)

val SupabaseTextPrimary = Color(0xFFEDEDED)
val SupabaseTextSecondary = Color(0xFFA1A1AA)
val SupabaseTextTertiary = Color(0xFF71717A)

// Platform Accents
val InstagramAccent = Color(0xFFE1306C)
val YoutubeAccent = Color(0xFFFF2D55)

@Immutable
data class BrainPalPalette(
    val isDark: Boolean,
    val canvasBackground: Color,
    val ambientGlowPrimary: Color,
    val ambientGlowSecondary: Color,
    val gridLineColor: Color,
    val cardGradientTop: Color,
    val cardGradientBottom: Color,
    val cardBorderTop: Color,
    val cardBorderBottom: Color,
    val hairlineDivider: Color,
    val segmentedTrackBg: Color,
    val segmentedTrackBorder: Color,
    val segmentedThumbBg: Color,
    val segmentedThumbBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primaryAccent: Color,
    val normalBarActive: Color,
    val normalBarDefault: Color,
    val normalBarMuted: Color,
    val barRecessedTrack: Color,
    val statusHealthy: Color,
    val statusTired: Color,
    val statusDrained: Color,
    val statusRot: Color
)

val LightBrainPalPalette = BrainPalPalette(
    isDark = false,
    canvasBackground = CupertinoCanvasBg,
    ambientGlowPrimary = Color(0xB3DCE8FA),
    ambientGlowSecondary = Color(0x8CE8E2F8),
    gridLineColor = Color(0x07000000),
    cardGradientTop = Color.White.copy(alpha = 0.90f),
    cardGradientBottom = Color(0xFFFDFDFE).copy(alpha = 0.80f),
    cardBorderTop = Color.White,
    cardBorderBottom = Color(0xFFE2E6EE),
    hairlineDivider = CupertinoHairline,
    segmentedTrackBg = Color(0xFFE3E5EC).copy(alpha = 0.78f),
    segmentedTrackBorder = Color.White.copy(alpha = 0.6f),
    segmentedThumbBg = Color.White,
    segmentedThumbBorder = Color.White,
    textPrimary = InkPrimary,
    textSecondary = InkSecondary,
    textTertiary = InkTertiary,
    primaryAccent = SystemBlue,
    normalBarActive = SystemBlue,
    normalBarDefault = SystemBlue.copy(alpha = 0.78f),
    normalBarMuted = Color(0xFFB8C2D1),
    barRecessedTrack = Color(0xFFEDF0F5),
    statusHealthy = SystemGreen,
    statusTired = SystemAmber,
    statusDrained = SystemOrange,
    statusRot = SystemRed
)

val SupabaseDarkBrainPalPalette = BrainPalPalette(
    isDark = true,
    canvasBackground = SupabaseCanvasBg,
    ambientGlowPrimary = Color(0x223ECF8E), // Subtle Supabase emerald top-left radial glow
    ambientGlowSecondary = Color(0x1810B981), // Subtle deep emerald/teal right glow
    gridLineColor = Color(0x0FFFFFFF), // Subtle architectural grid lines
    cardGradientTop = SupabaseSurfaceTop,
    cardGradientBottom = SupabaseSurfaceBottom,
    cardBorderTop = SupabaseBorderTop,
    cardBorderBottom = SupabaseBorderBottom,
    hairlineDivider = SupabaseHairline,
    segmentedTrackBg = SupabaseSegmentedTrack,
    segmentedTrackBorder = Color(0xFF262626),
    segmentedThumbBg = SupabaseSegmentedThumb,
    segmentedThumbBorder = Color(0xFF3A3A3A),
    textPrimary = SupabaseTextPrimary,
    textSecondary = SupabaseTextSecondary,
    textTertiary = SupabaseTextTertiary,
    primaryAccent = SupabaseEmerald,
    normalBarActive = SupabaseEmerald,
    normalBarDefault = SupabaseEmerald.copy(alpha = 0.80f),
    normalBarMuted = Color(0xFF343B38),
    barRecessedTrack = Color(0xFF1E1E1E),
    statusHealthy = SupabaseEmerald,
    statusTired = Color(0xFFFBBF24),
    statusDrained = Color(0xFFFB923C),
    statusRot = SupabaseCrimson
)

val LocalBrainPalPalette = staticCompositionLocalOf { LightBrainPalPalette }
