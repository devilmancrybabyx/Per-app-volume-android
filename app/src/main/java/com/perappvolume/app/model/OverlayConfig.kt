package com.perappvolume.app.model

enum class OverlayPosition { LEFT, RIGHT, CENTER }
enum class SliderOrientation { HORIZONTAL, VERTICAL }
enum class OverlayTheme { LIGHT, DARK, CUSTOM }
enum class OverlayTrigger { HARDWARE_BUTTONS, FLOATING_TRIGGER, NOTIFICATION_TILE, WIDGET }

data class OverlayConfig(
    val position: OverlayPosition = OverlayPosition.RIGHT,
    val verticalOffsetPx: Int = 0,
    val widthPx: Int = 320,
    val heightPx: Int = 480,
    val sliderOrientation: SliderOrientation = SliderOrientation.VERTICAL,
    val theme: OverlayTheme = OverlayTheme.DARK,
    val customColorArgb: Int? = null,
    val opacityPercent: Int = 90,
    val autoHideTimeoutMs: Long = 2500L,
    val trigger: OverlayTrigger = OverlayTrigger.HARDWARE_BUTTONS,
    val hapticIntensity: Int = 2,          // 0 = off, 1 = light, 2 = medium, 3 = strong
    val overrideEnabled: Boolean = true    // false = fall back to stock Android volume panel entirely
)
