package com.example.data

import androidx.room.TypeConverter

enum class BrainState(
    val label: String,
    val emoji: String,
    val subtitle: String,
    val minReels: Int,
    val maxReels: Int?
) {
    HEALTHY(
        label = "Healthy Brain",
        emoji = "🧠",
        subtitle = "Optimal neural focus & low dopamine fatigue",
        minReels = 0,
        maxReels = 30
    ),
    TIRED(
        label = "Tired Brain",
        emoji = "🥱",
        subtitle = "Early attention fragmentation detected",
        minReels = 31,
        maxReels = 75
    ),
    DRAINED(
        label = "Drained Brain",
        emoji = "😵‍💫",
        subtitle = "High scroll velocity & dopamine desensitization",
        minReels = 76,
        maxReels = 150
    ),
    BRAIN_ROT(
        label = "Brain Rot",
        emoji = "🧟",
        subtitle = "Critical short-form overload — take a break now",
        minReels = 151,
        maxReels = null
    );

    companion object {
        fun fromCount(totalReels: Int): BrainState {
            return when {
                totalReels <= 30 -> HEALTHY
                totalReels <= 75 -> TIRED
                totalReels <= 150 -> DRAINED
                else -> BRAIN_ROT
            }
        }
    }
}

class BrainStateConverters {
    @TypeConverter
    fun fromBrainState(state: BrainState): String = state.name

    @TypeConverter
    fun toBrainState(value: String): BrainState =
        runCatching { BrainState.valueOf(value) }.getOrDefault(BrainState.HEALTHY)
}

enum class MonitoredApp(
    val packageName: String,
    val displayName: String,
    val shortLabel: String
) {
    INSTAGRAM(
        packageName = "com.instagram.android",
        displayName = "Instagram Reels",
        shortLabel = "Instagram"
    ),
    YOUTUBE(
        packageName = "com.google.android.youtube",
        displayName = "YouTube Shorts",
        shortLabel = "YouTube"
    );

    companion object {
        val MONITORED_PACKAGES: Set<String> = entries.map { it.packageName }.toSet()

        fun fromPackage(packageName: String?): MonitoredApp? {
            if (packageName == null) return null
            return entries.firstOrNull { it.packageName == packageName }
        }
    }
}
