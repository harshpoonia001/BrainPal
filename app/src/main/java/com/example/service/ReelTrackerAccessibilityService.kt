package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.BrainPalApplication
import com.example.data.MonitoredApp
import com.example.data.ReelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Module 1: Real-Time Reel & Shorts Detection Engine (AccessibilityService)
 * - Strictly monitors Instagram Reels (com.instagram.android) and YouTube Shorts (com.google.android.youtube).
 * - Distinguishes YouTube Shorts & Instagram Reels full-screen vertical players from their Home Feeds,
 *   Search, Subscriptions, Stories trays, and Profile screens.
 * - Shows the Dynamic Island overlay ONLY when an Instagram Reel or YouTube Short is actively open,
 *   and immediately hides it on the YouTube/Instagram Home feed or Android Home screen.
 * - Enforces a mandatory 1.5-second (1,500ms) debounce window on vertical Reel/Short swipes.
 */
class ReelTrackerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: ReelRepository by lazy {
        (applicationContext as BrainPalApplication).repository
    }

    private var lastCountedSwipeElapsedMs: Long = 0L
    private var lastWindowInspectionElapsedMs: Long = 0L
    private var isOverlayCurrentlyShown: Boolean = false
    private var activeReelPackage: String? = null
    private var lastKnownReelPageIndex: Int = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository.setAccessibilityServiceConnected(true)
        hideDynamicIslandOverlay()
        Log.d(TAG, "BrainPal ReelTrackerAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore events emitted by our own overlay window or Android SystemUI status bar/notifications
        if (pkg == applicationContext.packageName || pkg == SYSTEM_UI_PACKAGE) {
            return
        }

        // If user switched to any non-monitored app (e.g., Android Home Launcher, Chrome, WhatsApp, etc.),
        // immediately hide the Dynamic Island overlay!
        if (pkg !in MonitoredApp.MONITORED_PACKAGES) {
            if (isOverlayCurrentlyShown || activeReelPackage != null) {
                lastKnownReelPageIndex = -1
                hideDynamicIslandOverlay()
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val isReelOrShortOpen = isDedicatedReelOrShortScreenOpen(pkg)
                updateOverlayVisibility(pkg, isReelOrShortOpen)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Throttle content-change screen state checks so we respond snappily to tab switches
                // (e.g. Shorts -> Home tab) without excessive tree traversals
                val now = SystemClock.elapsedRealtime()
                if (now - lastWindowInspectionElapsedMs >= WINDOW_CHECK_THROTTLE_MS) {
                    lastWindowInspectionElapsedMs = now
                    val isReelOrShortOpen = isDedicatedReelOrShortScreenOpen(pkg)
                    updateOverlayVisibility(pkg, isReelOrShortOpen)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // 1. Verify the active screen is genuinely YouTube Shorts or Instagram Reels
                //    (NEVER count scrolling on the YouTube Home Feed or Instagram Home Feed)
                val isReelOrShortOpen = isDedicatedReelOrShortScreenOpen(pkg)
                updateOverlayVisibility(pkg, isReelOrShortOpen)
                if (!isReelOrShortOpen) {
                    return
                }

                // 2. Verify this scroll event came from the vertical Reel/Short pager and not
                //    a horizontal carousel, comments list, or home feed recycler
                if (!isValidVerticalReelSwipeEvent(event, pkg)) {
                    return
                }

                // 3. Enforce mandatory 1.5-second debounce window
                val nowElapsed = SystemClock.elapsedRealtime()
                if (!shouldAcceptSwipeAfterDebounce(lastCountedSwipeElapsedMs, nowElapsed)) {
                    return
                }

                lastCountedSwipeElapsedMs = nowElapsed
                serviceScope.launch {
                    repository.recordReelSwipe(packageName = pkg)
                }
            }

            else -> Unit
        }
    }

    private fun updateOverlayVisibility(packageName: String, isReelOrShortOpen: Boolean) {
        if (isReelOrShortOpen) {
            if (!isOverlayCurrentlyShown || activeReelPackage != packageName) {
                isOverlayCurrentlyShown = true
                activeReelPackage = packageName
                repository.onReelOrShortScreenEntered(packageName)
                FloatingOverlayService.showOverlay(applicationContext)
            }
        } else {
            if (isOverlayCurrentlyShown || activeReelPackage != null) {
                lastKnownReelPageIndex = -1
                hideDynamicIslandOverlay()
            }
        }
    }

    private fun hideDynamicIslandOverlay() {
        isOverlayCurrentlyShown = false
        activeReelPackage = null
        repository.onReelOrShortScreenExited()
        FloatingOverlayService.hideOverlay(applicationContext)
    }

    /**
     * Inspects the active window hierarchy to determine if the user is specifically inside
     * YouTube Shorts or Instagram Reels (returning false on Home Feeds, Search, Subscriptions, etc.).
     */
    private fun isDedicatedReelOrShortScreenOpen(packageName: String): Boolean {
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return false

        return try {
            when (packageName) {
                MonitoredApp.YOUTUBE.packageName -> isYoutubeShortsActive(root)
                MonitoredApp.INSTAGRAM.packageName -> isInstagramReelsActive(root)
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting window state for $packageName", e)
            false
        }
    }

    /**
     * YouTube Shorts vs YouTube Home Feed detector:
     * - Returns FALSE if the "Home", "Subscriptions", or "You" bottom tab is selected,
     *   or if Home feed filter chips / home results feed is visible without a Shorts player.
     * - Returns TRUE only when the Shorts player view IDs (`reel_recycler`, `reel_watch`, `reel_player`,
     *   `shorts_player`) are visible OR the "Shorts" bottom tab is actively selected (`isSelected == true`)
     *   or full-screen Shorts overlay controls ("Remix" + vertical action rail) are visible.
     */
    private fun isYoutubeShortsActive(root: AccessibilityNodeInfo): Boolean {
        var hasExplicitShortsViewId = false
        var isShortsTabSelected = false
        var isNonShortsTabSelected = false
        var hasHomeFeedIndicators = false
        var hasRemixAction = false
        var hasSubscribeOverlay = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODE_TRAVERSAL) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser) {
                val viewId = node.viewIdResourceName.orEmpty().lowercase()
                val text = node.text?.toString()?.trim().orEmpty().lowercase()
                val desc = node.contentDescription?.toString()?.trim().orEmpty().lowercase()

                // 1. Explicit YouTube Shorts player view IDs
                if (
                    viewId.contains("reel_recycler") ||
                    viewId.contains("reel_watch") ||
                    viewId.contains("reel_player") ||
                    viewId.contains("reel_right_cell") ||
                    viewId.contains("reel_dyn_bar") ||
                    viewId.contains("reel_progress") ||
                    viewId.contains("shorts_player") ||
                    viewId.contains("shorts_page")
                ) {
                    hasExplicitShortsViewId = true
                }

                // 2. YouTube Home feed / regular watch player view IDs
                if (
                    viewId.contains("chip_cloud") ||
                    viewId.contains("home_feed") ||
                    viewId.contains("watch_while_time_bar")
                ) {
                    hasHomeFeedIndicators = true
                }

                // 3. Bottom Navigation Bar selected tab check
                if (node.isSelected) {
                    if (text == "shorts" || desc == "shorts" || desc.startsWith("shorts")) {
                        isShortsTabSelected = true
                    } else if (
                        text == "home" || desc == "home" ||
                        text == "subscriptions" || desc.contains("subscriptions") ||
                        text == "you" || text == "library" || desc == "you" || desc == "library"
                    ) {
                        isNonShortsTabSelected = true
                    }
                }

                // 4. Full-screen Shorts overlay controls
                if (text == "remix" || desc == "remix" || desc.contains("remix this short")) {
                    hasRemixAction = true
                }
                if (text.startsWith("subscribe") || desc.startsWith("subscribe to")) {
                    hasSubscribeOverlay = true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (hasExplicitShortsViewId) {
            return true
        }
        if (isNonShortsTabSelected || hasHomeFeedIndicators) {
            return false
        }
        return isShortsTabSelected || (hasRemixAction && hasSubscribeOverlay)
    }

    /**
     * Instagram Reels vs Instagram Home Feed / Stories detector:
     * - Note: Instagram names the Home Feed Stories bar `reel_tray_container`! We explicitly treat
     *   `reel_tray` and selected "Home"/"Search"/"Profile" tabs as Home Feed (FALSE).
     * - Instagram names actual Reels `clips_viewer_view_pager`, `clips_video_container`, `clips_tab`, etc.
     */
    private fun isInstagramReelsActive(root: AccessibilityNodeInfo): Boolean {
        var hasExplicitClipsViewId = false
        var isReelsTabSelected = false
        var isNonReelsTabSelected = false
        var hasHomeStoriesTray = false
        var hasReelsAudioOverlay = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODE_TRAVERSAL) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser) {
                val viewId = node.viewIdResourceName.orEmpty().lowercase()
                val text = node.text?.toString()?.trim().orEmpty().lowercase()
                val desc = node.contentDescription?.toString()?.trim().orEmpty().lowercase()

                // 1. Home feed Stories tray (`reel_tray`) or main feed list -> NOT Reels!
                if (
                    viewId.contains("reel_tray") ||
                    viewId.contains("main_feed") ||
                    desc == "your story" ||
                    text == "your story"
                ) {
                    hasHomeStoriesTray = true
                }

                // 2. Explicit Instagram Reels (`clips_*`) view IDs
                if (
                    viewId.contains("clips_viewer") ||
                    viewId.contains("clips_video") ||
                    viewId.contains("clips_item") ||
                    viewId.contains("clips_ufi") ||
                    viewId.contains("clips_author") ||
                    viewId.contains("clips_pager")
                ) {
                    hasExplicitClipsViewId = true
                }

                // 3. Bottom Navigation Bar selected tab check
                if (node.isSelected) {
                    if (desc == "reels" || viewId.endsWith("/clips_tab")) {
                        isReelsTabSelected = true
                    } else if (
                        desc == "home" ||
                        desc.contains("search and explore") ||
                        desc == "profile" ||
                        viewId.endsWith("/feed_tab") ||
                        viewId.endsWith("/search_tab") ||
                        viewId.endsWith("/profile_tab")
                    ) {
                        isNonReelsTabSelected = true
                    }
                }

                // 4. Full-screen Reels audio & attribution overlay
                if (
                    desc.contains("reel by ") ||
                    text.contains("original audio") ||
                    desc.contains("original audio")
                ) {
                    hasReelsAudioOverlay = true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (hasHomeStoriesTray || isNonReelsTabSelected) {
            return false
        }
        return hasExplicitClipsViewId || isReelsTabSelected || hasReelsAudioOverlay
    }

    /**
     * Validates that a TYPE_VIEW_SCROLLED event inside an active Shorts/Reels screen
     * represents a vertical swipe to a new Short/Reel rather than a horizontal swipe or micro-scroll.
     */
    private fun isValidVerticalReelSwipeEvent(
        event: AccessibilityEvent,
        packageName: String
    ): Boolean {
        val deltaX = event.scrollDeltaX
        val deltaY = event.scrollDeltaY

        // Reject horizontal scrolls or tiny jitter scrolls when deltas are reported
        if (deltaX != 0 || deltaY != 0) {
            if (abs(deltaX) > abs(deltaY)) return false
            if (abs(deltaY) < MIN_VERTICAL_SCROLL_DELTA_PX) return false
        }

        val sourceViewId = event.source?.viewIdResourceName.orEmpty().lowercase()

        // Reject known non-reel scroll containers (e.g., home feed results, filter chips, comments list)
        if (
            sourceViewId.contains("results") ||
            sourceViewId.contains("chip_cloud") ||
            sourceViewId.contains("reel_tray") ||
            sourceViewId.contains("comment")
        ) {
            return false
        }

        // If the pager/recycler reports item indices (fromIndex), ensure the page index actually changed
        if (event.fromIndex >= 0 && event.itemCount > 1) {
            if (event.fromIndex == lastKnownReelPageIndex) {
                return false
            }
            lastKnownReelPageIndex = event.fromIndex
            return true
        }

        val className = event.className?.toString().orEmpty()
        return className.contains("ViewPager", ignoreCase = true) ||
            className.contains("RecyclerView", ignoreCase = true) ||
            sourceViewId.contains("reel_recycler") ||
            sourceViewId.contains("clips_viewer") ||
            deltaY > MIN_VERTICAL_SCROLL_DELTA_PX
    }

    override fun onInterrupt() {
        hideDynamicIslandOverlay()
        repository.setAccessibilityServiceConnected(false)
    }

    override fun onDestroy() {
        hideDynamicIslandOverlay()
        repository.setAccessibilityServiceConnected(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ReelTrackerA11yService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val MAX_NODE_TRAVERSAL = 80
        private const val WINDOW_CHECK_THROTTLE_MS = 180L
        private const val MIN_VERTICAL_SCROLL_DELTA_PX = 40
        const val DEBOUNCE_WINDOW_MS = 1_500L // Mandatory 1.5-second debounce window

        fun shouldAcceptSwipeAfterDebounce(
            lastSwipeElapsedMs: Long,
            currentElapsedMs: Long
        ): Boolean {
            if (lastSwipeElapsedMs == 0L) return true
            return (currentElapsedMs - lastSwipeElapsedMs) >= DEBOUNCE_WINDOW_MS
        }
    }
}
