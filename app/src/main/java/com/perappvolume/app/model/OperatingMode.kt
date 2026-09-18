package com.perappvolume.app.model

/**
 * Live  — record new changes as the user makes them
 * Play  — apply saved levels only, never record
 * Stop  — feature fully disabled, standard Android volume behavior
 */
enum class OperatingMode {
    LIVE, PLAY, STOP
}
