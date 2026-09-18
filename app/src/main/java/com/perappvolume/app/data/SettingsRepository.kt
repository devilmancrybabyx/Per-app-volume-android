package com.perappvolume.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.perappvolume.app.model.EdgeSide
import com.perappvolume.app.model.FloatingButtonConfig
import com.perappvolume.app.model.FloatingButtonShape
import com.perappvolume.app.model.OperatingMode
import com.perappvolume.app.model.OverlayConfig
import com.perappvolume.app.model.OverlayPosition
import com.perappvolume.app.model.OverlayTheme
import com.perappvolume.app.model.OverlayTrigger
import com.perappvolume.app.model.SliderOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Single source of truth for user-configurable, non-per-app settings:
 * operating mode, overlay appearance/behavior, floating button config, and
 * the global fallback volume used for apps that have never been explicitly
 * adjusted (spec section 7).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("operating_mode")

        val POSITION = stringPreferencesKey("overlay_position")
        val V_OFFSET = intPreferencesKey("overlay_v_offset")
        val WIDTH = intPreferencesKey("overlay_width")
        val HEIGHT = intPreferencesKey("overlay_height")
        val ORIENTATION = stringPreferencesKey("slider_orientation")
        val THEME = stringPreferencesKey("overlay_theme")
        val CUSTOM_COLOR = intPreferencesKey("overlay_custom_color")
        val OPACITY = intPreferencesKey("overlay_opacity")
        val TIMEOUT = longPreferencesKey("overlay_timeout_ms")
        val TRIGGER = stringPreferencesKey("overlay_trigger")
        val HAPTIC = intPreferencesKey("overlay_haptic")
        val OVERRIDE_ENABLED = booleanPreferencesKey("overlay_override_enabled")

        val GLOBAL_FALLBACK_VOLUME = intPreferencesKey("global_fallback_volume")

        // Floating buttons
        val EDGE_TAB_ENABLED = booleanPreferencesKey("edge_tab_enabled")
        val EDGE_TAB_SIDE = stringPreferencesKey("edge_tab_side")
        val EDGE_TAB_V_PERCENT = floatPreferencesKey("edge_tab_v_percent")
        val EDGE_TAB_SIZE = intPreferencesKey("edge_tab_size")
        val EDGE_TAB_OPACITY = intPreferencesKey("edge_tab_opacity")
        val EDGE_TAB_THRESHOLD = intPreferencesKey("edge_tab_threshold")

        val PERSISTENT_ENABLED = booleanPreferencesKey("persistent_btn_enabled")
        val PERSISTENT_SHAPE = stringPreferencesKey("persistent_btn_shape")
        val PERSISTENT_SIZE = intPreferencesKey("persistent_btn_size")
        val PERSISTENT_OPACITY = intPreferencesKey("persistent_btn_opacity")
        val PERSISTENT_X = intPreferencesKey("persistent_btn_x")
        val PERSISTENT_Y = intPreferencesKey("persistent_btn_y")
        val PERSISTENT_STREAM = intPreferencesKey("persistent_btn_stream")
        val PERSISTENT_PX_PER_STEP = intPreferencesKey("persistent_btn_px_per_step")
    }

    val mode: Flow<OperatingMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.MODE]?.let { runCatching { OperatingMode.valueOf(it) }.getOrNull() }
            ?: OperatingMode.LIVE
    }

    suspend fun setMode(mode: OperatingMode) {
        context.dataStore.edit { it[Keys.MODE] = mode.name }
    }

    val overlayConfig: Flow<OverlayConfig> = context.dataStore.data.map { prefs -> prefs.toOverlayConfig() }

    private fun Preferences.toOverlayConfig(): OverlayConfig = OverlayConfig(
        position = this[Keys.POSITION]?.let { runCatching { OverlayPosition.valueOf(it) }.getOrNull() }
            ?: OverlayPosition.RIGHT,
        verticalOffsetPx = this[Keys.V_OFFSET] ?: 0,
        widthPx = this[Keys.WIDTH] ?: 320,
        heightPx = this[Keys.HEIGHT] ?: 480,
        sliderOrientation = this[Keys.ORIENTATION]?.let { runCatching { SliderOrientation.valueOf(it) }.getOrNull() }
            ?: SliderOrientation.VERTICAL,
        theme = this[Keys.THEME]?.let { runCatching { OverlayTheme.valueOf(it) }.getOrNull() }
            ?: OverlayTheme.DARK,
        customColorArgb = this[Keys.CUSTOM_COLOR],
        opacityPercent = this[Keys.OPACITY] ?: 90,
        autoHideTimeoutMs = this[Keys.TIMEOUT] ?: 2500L,
        trigger = this[Keys.TRIGGER]?.let { runCatching { OverlayTrigger.valueOf(it) }.getOrNull() }
            ?: OverlayTrigger.HARDWARE_BUTTONS,
        hapticIntensity = this[Keys.HAPTIC] ?: 2,
        overrideEnabled = this[Keys.OVERRIDE_ENABLED] ?: true
    )

    /** Reads the current stored config, applies [update] to it, then persists the merged result. */
    suspend fun updateOverlayConfig(update: (OverlayConfig) -> OverlayConfig) {
        val current = context.dataStore.data.first().toOverlayConfig()
        val new = update(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.POSITION] = new.position.name
            prefs[Keys.V_OFFSET] = new.verticalOffsetPx
            prefs[Keys.WIDTH] = new.widthPx
            prefs[Keys.HEIGHT] = new.heightPx
            prefs[Keys.ORIENTATION] = new.sliderOrientation.name
            prefs[Keys.THEME] = new.theme.name
            new.customColorArgb?.let { prefs[Keys.CUSTOM_COLOR] = it }
            prefs[Keys.OPACITY] = new.opacityPercent
            prefs[Keys.TIMEOUT] = new.autoHideTimeoutMs
            prefs[Keys.TRIGGER] = new.trigger.name
            prefs[Keys.HAPTIC] = new.hapticIntensity
            prefs[Keys.OVERRIDE_ENABLED] = new.overrideEnabled
        }
    }

    val floatingButtonConfig: Flow<FloatingButtonConfig> = context.dataStore.data.map { prefs -> prefs.toFloatingButtonConfig() }

    private fun Preferences.toFloatingButtonConfig(): FloatingButtonConfig = FloatingButtonConfig(
        edgeTabEnabled = this[Keys.EDGE_TAB_ENABLED] ?: false,
        edgeTabSide = this[Keys.EDGE_TAB_SIDE]?.let { runCatching { EdgeSide.valueOf(it) }.getOrNull() }
            ?: EdgeSide.RIGHT,
        edgeTabVerticalPercent = this[Keys.EDGE_TAB_V_PERCENT] ?: 0.5f,
        edgeTabSizeDp = this[Keys.EDGE_TAB_SIZE] ?: 28,
        edgeTabOpacityPercent = this[Keys.EDGE_TAB_OPACITY] ?: 40,
        edgeTabPullThresholdDp = this[Keys.EDGE_TAB_THRESHOLD] ?: 56,
        persistentButtonEnabled = this[Keys.PERSISTENT_ENABLED] ?: false,
        persistentShape = this[Keys.PERSISTENT_SHAPE]?.let { runCatching { FloatingButtonShape.valueOf(it) }.getOrNull() }
            ?: FloatingButtonShape.CIRCLE,
        persistentSizeDp = this[Keys.PERSISTENT_SIZE] ?: 56,
        persistentOpacityPercent = this[Keys.PERSISTENT_OPACITY] ?: 70,
        persistentPositionX = this[Keys.PERSISTENT_X] ?: 40,
        persistentPositionY = this[Keys.PERSISTENT_Y] ?: 600,
        persistentTargetStream = this[Keys.PERSISTENT_STREAM] ?: android.media.AudioManager.STREAM_MUSIC,
        persistentPxPerVolumeStep = this[Keys.PERSISTENT_PX_PER_STEP] ?: 40
    )

    suspend fun updateFloatingButtonConfig(update: (FloatingButtonConfig) -> FloatingButtonConfig) {
        val current = context.dataStore.data.first().toFloatingButtonConfig()
        val new = update(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.EDGE_TAB_ENABLED] = new.edgeTabEnabled
            prefs[Keys.EDGE_TAB_SIDE] = new.edgeTabSide.name
            prefs[Keys.EDGE_TAB_V_PERCENT] = new.edgeTabVerticalPercent
            prefs[Keys.EDGE_TAB_SIZE] = new.edgeTabSizeDp
            prefs[Keys.EDGE_TAB_OPACITY] = new.edgeTabOpacityPercent
            prefs[Keys.EDGE_TAB_THRESHOLD] = new.edgeTabPullThresholdDp

            prefs[Keys.PERSISTENT_ENABLED] = new.persistentButtonEnabled
            prefs[Keys.PERSISTENT_SHAPE] = new.persistentShape.name
            prefs[Keys.PERSISTENT_SIZE] = new.persistentSizeDp
            prefs[Keys.PERSISTENT_OPACITY] = new.persistentOpacityPercent
            prefs[Keys.PERSISTENT_X] = new.persistentPositionX
            prefs[Keys.PERSISTENT_Y] = new.persistentPositionY
            prefs[Keys.PERSISTENT_STREAM] = new.persistentTargetStream
            prefs[Keys.PERSISTENT_PX_PER_STEP] = new.persistentPxPerVolumeStep
        }
    }

    /** Called by the persistent button's drag-to-reposition gesture; cheaper than the general update() path. */
    suspend fun savePersistentButtonPosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERSISTENT_X] = x
            prefs[Keys.PERSISTENT_Y] = y
        }
    }

    val globalFallbackVolume: Flow<Int> = context.dataStore.data.map { it[Keys.GLOBAL_FALLBACK_VOLUME] ?: -1 }

    suspend fun setGlobalFallbackVolume(level: Int) {
        context.dataStore.edit { it[Keys.GLOBAL_FALLBACK_VOLUME] = level }
    }
}
