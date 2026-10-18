package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.BrainPalApplication
import com.example.MainActivity
import com.example.R
import com.example.ui.overlay.DynamicIslandOverlayView
import com.example.ui.theme.BrainPalTheme

/**
 * Module 2: System-level "Dynamic Island" Capsule Overlay Service
 * Renders a floating pill at the top-center camera cutout area using
 * WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY with zero input blocking
 * (FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCH_MODAL).
 * Strictly visible ONLY when Instagram Reels or YouTube Shorts is actively open.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayWindowLifecycleOwner? = null
    private var isAddedToWindow = false

    override fun onCreate() {
        super.onCreate()
        startOverlayForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> {
                removeOverlayView()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                removeOverlayView()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_OVERLAY -> {
                showOrUpdateOverlayView()
            }
            else -> {
                val repo = (applicationContext as? BrainPalApplication)?.repository
                if (repo?.liveTelemetry?.value?.isOverlayVisible == true) {
                    showOrUpdateOverlayView()
                } else {
                    removeOverlayView()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun showOrUpdateOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted yet; skipping system window attach")
            return
        }
        if (isAddedToWindow && overlayView != null) {
            return
        }

        try {
            val lifecycleOwner = OverlayWindowLifecycleOwner().also {
                it.performRestore()
                it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                it.handleLifecycleEvent(Lifecycle.Event.ON_START)
                it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            overlayLifecycleOwner = lifecycleOwner

            val repository = (applicationContext as BrainPalApplication).repository

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    BrainPalTheme {
                        val telemetry by repository.liveTelemetry.collectAsState()
                        if (telemetry.isOverlayVisible && telemetry.activeApp != null) {
                            DynamicIslandOverlayView(
                                totalReels = telemetry.todayTotalReels,
                                brainState = telemetry.brainState,
                                activeApp = telemetry.activeApp,
                                lastIncrementTimestampMs = telemetry.lastIncrementTimestampMs,
                                showExtendedStatus = true
                            )
                        }
                    }
                }
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 24 // Pinned at top-center below status bar / camera cutout
            }

            windowManager?.addView(composeView, params)
            overlayView = composeView
            isAddedToWindow = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Dynamic Island overlay view", e)
        }
    }

    private fun removeOverlayView() {
        if (isAddedToWindow && overlayView != null) {
            runCatching {
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                windowManager?.removeView(overlayView)
            }
        }
        overlayView = null
        overlayLifecycleOwner = null
        isAddedToWindow = false
    }

    private fun startOverlayForegroundNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_notification_text)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "brainpal_overlay_channel"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.action.HIDE_OVERLAY"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_OVERLAY_SERVICE"

        fun showOverlay(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hideOverlay(context: Context) {
            runCatching {
                context.stopService(Intent(context, FloatingOverlayService::class.java))
            }
        }
    }
}

private class OverlayWindowLifecycleOwner :
    androidx.lifecycle.LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun performRestore() {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
