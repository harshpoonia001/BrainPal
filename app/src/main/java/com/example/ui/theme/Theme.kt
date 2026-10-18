package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val BrainPalLightColorScheme = lightColorScheme(
    primary = SystemBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = SystemGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9FBE3),
    onSecondaryContainer = Color(0xFF063B16),
    tertiary = SystemAmber,
    onTertiary = Color.White,
    error = SystemRed,
    background = CupertinoCanvasBg,
    onBackground = InkPrimary,
    surface = GlassSurfaceElevated,
    onSurface = InkPrimary,
    surfaceVariant = CupertinoSegmentedTrack,
    onSurfaceVariant = InkSecondary,
    outline = CupertinoHairline
)

private val BrainPalSupabaseDarkColorScheme = darkColorScheme(
    primary = SupabaseEmerald,
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = SupabaseEmerald,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF1F2937),
    onSecondaryContainer = SupabaseTextPrimary,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF1C1917),
    error = SupabaseCrimson,
    background = SupabaseCanvasBg,
    onBackground = SupabaseTextPrimary,
    surface = Color(0xFF1C1C1C),
    onSurface = SupabaseTextPrimary,
    surfaceVariant = SupabaseSegmentedTrack,
    onSurfaceVariant = SupabaseTextSecondary,
    outline = SupabaseHairline
)

@Composable
fun BrainPalTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BrainPalSupabaseDarkColorScheme else BrainPalLightColorScheme
    val palette = if (darkTheme) SupabaseDarkBrainPalPalette else LightBrainPalPalette

    CompositionLocalProvider(LocalBrainPalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    BrainPalTheme(darkTheme = darkTheme, content = content)
}
