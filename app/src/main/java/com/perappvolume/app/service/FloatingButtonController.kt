package com.perappvolume.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.perappvolume.app.data.SettingsRepository
import com.perappvolume.app.model.EdgeSide
import com.perappvolume.app.model.FloatingButtonConfig
import com.perappvolume.app.model.FloatingButtonShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Owns the two optional floating widgets described in the spec:
 *  - an edge pull-tab that opens the full volume overlay when dragged inward
 *  - a persistent button that adjusts one chosen stream directly on a
 *    vertical swipe, and can be repositioned with a horizontal drag
 *
 * Neither widget uses Compose — plain Views are simpler to drag smoothly and
 * cheaper to keep alive continuously alongside the overlay's ComposeView.
 */
class FloatingButtonController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val audioManager: AudioManager,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onOpenOverlay: () -> Unit
) {
    private var edgeTabView: View? = null
    private var edgeTabParams: WindowManager.LayoutParams? = null

    private var persistentView: View? = null
    private var persistentParams: WindowManager.LayoutParams? = null

    private val vibrator: Vibrator? by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastConfig: FloatingButtonConfig? = null

    fun applyConfig(config: FloatingButtonConfig) {
        lastConfig = config

        if (config.edgeTabEnabled) {
            if (edgeTabView == null) addEdgeTab(config) else updateEdgeTab(config)
        } else {
            removeEdgeTab()
        }

        if (config.persistentButtonEnabled) {
            if (persistentView == null) addPersistentButton(config) else updatePersistentButton(config)
        } else {
            removePersistentButton()
        }
    }

    fun teardown() {
        removeEdgeTab()
        removePersistentButton()
    }

    // ---------------------------------------------------------------- edge tab

    private fun addEdgeTab(config: FloatingButtonConfig) {
        val sizePx = dp(config.edgeTabSizeDp)
        val view = FrameLayout(context).apply {
            background = buildDrawable(FloatingButtonShape.ROUNDED_SQUARE, config.edgeTabOpacityPercent, ACCENT_COLOR)
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx * 2,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (config.edgeTabSide == EdgeSide.LEFT) Gravity.START else Gravity.END
            y = (screenHeightPx() * config.edgeTabVerticalPercent).roundToInt()
            x = 0
        }

        var pulled = 0f
        val thresholdPx = dp(config.edgeTabPullThresholdDp)
        var downRawX = 0f

        view.setOnTouchListener { _, event ->
            val cfg = lastConfig ?: config
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    pulled = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    // Dragging "inward" means toward center: negative dx on the right edge, positive dx on the left edge.
                    pulled = if (cfg.edgeTabSide == EdgeSide.RIGHT) (-dx).coerceAtLeast(0f) else dx.coerceAtLeast(0f)
                    params.x = if (cfg.edgeTabSide == EdgeSide.RIGHT) -pulled.roundToInt() else pulled.roundToInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pulled >= thresholdPx) {
                        tick()
                        onOpenOverlay()
                    }
                    params.x = 0
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager.addView(view, params) }
        edgeTabView = view
        edgeTabParams = params
    }

    private fun updateEdgeTab(config: FloatingButtonConfig) {
        val view = edgeTabView ?: return
        val params = edgeTabParams ?: return
        val sizePx = dp(config.edgeTabSizeDp)
        view.background = buildDrawable(FloatingButtonShape.ROUNDED_SQUARE, config.edgeTabOpacityPercent, ACCENT_COLOR)
        params.width = sizePx
        params.height = sizePx * 2
        params.gravity = Gravity.TOP or if (config.edgeTabSide == EdgeSide.LEFT) Gravity.START else Gravity.END
        params.y = (screenHeightPx() * config.edgeTabVerticalPercent).roundToInt()
        params.x = 0
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removeEdgeTab() {
        edgeTabView?.let { runCatching { windowManager.removeView(it) } }
        edgeTabView = null
        edgeTabParams = null
    }

    // ------------------------------------------------------- persistent button

    private fun addPersistentButton(config: FloatingButtonConfig) {
        val sizePx = dp(config.persistentSizeDp)
        val view = FrameLayout(context).apply {
            background = buildDrawable(config.persistentShape, config.persistentOpacityPercent, ACCENT_COLOR)
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.persistentPositionX
            y = config.persistentPositionY
        }

        attachPersistentTouchHandling(view, params)

        runCatching { windowManager.addView(view, params) }
        persistentView = view
        persistentParams = params
    }

    private fun updatePersistentButton(config: FloatingButtonConfig) {
        val view = persistentView ?: return
        val params = persistentParams ?: return
        val sizePx = dp(config.persistentSizeDp)
        view.background = buildDrawable(config.persistentShape, config.persistentOpacityPercent, ACCENT_COLOR)
        params.width = sizePx
        params.height = sizePx
        // Only re-seed x/y from settings if the view isn't currently mid-drag;
        // touch handling below keeps params in sync and writes back on release.
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removePersistentButton() {
        persistentView?.let { runCatching { windowManager.removeView(it) } }
        persistentView = null
        persistentParams = null
    }

    private enum class Gesture { UNDECIDED, VOLUME, REPOSITION }

    private fun attachPersistentTouchHandling(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var downParamsX = 0
        var downParamsY = 0
        var lastVolumeAnchorY = 0f
        var gesture = Gesture.UNDECIDED
        val touchSlopPx = dp(8)

        view.setOnTouchListener { _, event ->
            val cfg = lastConfig ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamsX = params.x
                    downParamsY = params.y
                    lastVolumeAnchorY = event.rawY
                    gesture = Gesture.UNDECIDED
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (gesture == Gesture.UNDECIDED && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                        gesture = if (abs(dy) >= abs(dx)) Gesture.VOLUME else Gesture.REPOSITION
                    }

                    when (gesture) {
                        Gesture.VOLUME -> {
                            val movedSinceAnchor = lastVolumeAnchorY - event.rawY // positive = swiped up
                            val steps = (movedSinceAnchor / cfg.persistentPxPerVolumeStep).toInt()
                            if (steps != 0) {
                                repeat(abs(steps)) {
                                    val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                    audioManager.adjustStreamVolume(cfg.persistentTargetStream, direction, 0)
                                }
                                tick()
                                lastVolumeAnchorY -= steps * cfg.persistentPxPerVolumeStep
                            }
                        }
                        Gesture.REPOSITION -> {
                            params.x = downParamsX + dx.roundToInt()
                            params.y = downParamsY + dy.roundToInt()
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                        Gesture.UNDECIDED -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when (gesture) {
                        Gesture.UNDECIDED -> onOpenOverlay() // plain tap: open the full panel
                        Gesture.REPOSITION -> scope.launch {
                            settingsRepository.savePersistentButtonPosition(params.x, params.y)
                        }
                        Gesture.VOLUME -> Unit
                    }
                    gesture = Gesture.UNDECIDED
                    true
                }
                else -> false
            }
        }
    }

    // ------------------------------------------------------------------- utils

    private fun tick() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(15)
        }
    }

    private fun buildDrawable(shape: FloatingButtonShape, opacityPercent: Int, colorInt: Int): GradientDrawable {
        val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100)
        val color = Color.argb(alpha, Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
        return GradientDrawable().apply {
            setColor(color)
            this.shape = when (shape) {
                FloatingButtonShape.CIRCLE -> GradientDrawable.OVAL
                else -> GradientDrawable.RECTANGLE
            }
            cornerRadius = when (shape) {
                FloatingButtonShape.ROUNDED_SQUARE -> dp(12).toFloat()
                else -> 0f
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun screenHeightPx(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    companion object {
        private const val ACCENT_COLOR = 0xFF6650A4.toInt()
    }
}
