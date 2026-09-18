package com.perappvolume.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.perappvolume.app.R
import com.perappvolume.app.data.AppVolumeDatabase
import com.perappvolume.app.data.AppVolumeEntity
import com.perappvolume.app.data.SettingsRepository
import com.perappvolume.app.model.OperatingMode
import com.perappvolume.app.model.OverlayConfig
import com.perappvolume.app.overlay.VolumeOverlayContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service (spec sections 1-3) that:
 *  - keeps the accessibility/notification-listener pipeline alive under Doze,
 *  - owns the WindowManager overlay (TYPE_APPLICATION_OVERLAY) shown instead
 *    of the stock volume panel,
 *  - applies the priority/fallback logic when adjusting volume: per-package
 *    memory is written in LIVE mode, read-only in PLAY mode, bypassed
 *    entirely in STOP mode.
 */
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_VOLUME_KEY = "com.perappvolume.app.action.VOLUME_KEY"
        const val EXTRA_DIRECTION = "direction"
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile private var overrideEnabled = true
        fun isOverrideCurrentlyEnabled(): Boolean = overrideEnabled
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var database: AppVolumeDatabase

    private var overlayView: ComposeView? = null
    private var currentMode: OperatingMode = OperatingMode.LIVE
    private var currentConfig: OverlayConfig = OverlayConfig()
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeOverlay() }

    private var floatingButtonController: FloatingButtonController? = null

        override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settingsRepository = SettingsRepository(applicationContext)
        database = AppVolumeDatabase.getInstance(applicationContext)

        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            settingsRepository.mode.collect { currentMode = it }
        }
        serviceScope.launch {
            settingsRepository.overlayConfig.collect {
                currentConfig = it
                overrideEnabled = it.overrideEnabled
            }
        }

        floatingButtonController = FloatingButtonController(
            context = applicationContext,
            windowManager = windowManager,
            audioManager = audioManager,
            settingsRepository = settingsRepository,
            scope = serviceScope,
            onOpenOverlay = { requestShowOverlay() }
        )
        serviceScope.launch {
            settingsRepository.floatingButtonConfig.collect { config ->
                floatingButtonController?.applyConfig(config)
            }
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_VOLUME_KEY) {
            val direction = intent.getIntExtra(EXTRA_DIRECTION, 0)
            handleVolumeKey(direction)
        }
        return START_STICKY
    }

    private fun handleVolumeKey(direction: Int) {
        val targetPackage = ActiveAudioTracker.resolveTargetPackage()
        val stream = AudioManager.STREAM_MUSIC // media stream is the common case per spec's examples

        if (currentMode != OperatingMode.STOP) {
            val step = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(stream, step, 0)

            val newLevel = audioManager.getStreamVolume(stream)
            if (currentMode == OperatingMode.LIVE && targetPackage != null) {
                serviceScope.launch {
                    val existing = database.appVolumeDao().get(targetPackage)
                    if (existing?.overrideEnabled != false) {
                        database.appVolumeDao().upsert(
                            AppVolumeEntity(
                                packageName = targetPackage,
                                displayName = targetPackage,
                                streamType = stream,
                                volumeLevel = newLevel,
                                isMuted = existing?.isMuted ?: false
                            )
                        )
                    }
                }
            }
        }

        showOverlay(targetPackage)
    }

    /** Called by PLAY mode consumers (e.g. when a tracked app becomes the active target) to re-apply a saved level. */
    fun applySavedLevelIfNeeded(packageName: String) {
        if (currentMode != OperatingMode.PLAY) return
        serviceScope.launch {
            val saved = database.appVolumeDao().get(packageName) ?: return@launch
            if (!saved.overrideEnabled) return@launch
            audioManager.setStreamVolume(
                saved.streamType,
                saved.volumeLevel,
                0
            )
        }
    }

    /** Public entry point for anything that should open the full panel without a hardware key press: the edge pull-tab and a tap on the persistent button. */
    fun requestShowOverlay() {
        showOverlay(ActiveAudioTracker.resolveTargetPackage())
    }

    private fun showOverlay(activePackage: String?) {
        if (!currentConfig.overrideEnabled) return
        hideHandler.removeCallbacks(hideRunnable)

        if (overlayView == null) {
            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent {
                    VolumeOverlayContent(
                        config = currentConfig,
                        activePackage = activePackage,
                        audioManager = audioManager,
                        activeSourcesProvider = { ActiveAudioTracker.activeSources.value },
                        onDismiss = { removeOverlay() }
                    )
                }
            }
            val params = buildLayoutParams()
            windowManager.addView(composeView, params)
            overlayView = composeView
        }

        hideHandler.postDelayed(hideRunnable, currentConfig.autoHideTimeoutMs)
    }

    private fun removeOverlay() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val gravity = when (currentConfig.position) {
            com.perappvolume.app.model.OverlayPosition.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            com.perappvolume.app.model.OverlayPosition.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            com.perappvolume.app.model.OverlayPosition.CENTER -> Gravity.CENTER
        }
        return WindowManager.LayoutParams(
            currentConfig.widthPx,
            currentConfig.heightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.y = currentConfig.verticalOffsetPx
        }
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.overlay_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.perappvolume.app.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        floatingButtonController?.teardown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
