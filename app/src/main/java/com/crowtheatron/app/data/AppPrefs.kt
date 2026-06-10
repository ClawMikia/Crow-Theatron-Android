package com.crowtheatron.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var defaultSeekJumpSec: Int
        get() = p.getInt(KEY_SEEK_JUMP, 10)
        set(v) = p.edit().putInt(KEY_SEEK_JUMP, v.coerceAtLeast(1)).apply()

    var defaultPitchStepSemitones: Float
        get() = p.getFloat(KEY_PITCH_STEP, 1f)
        set(v) = p.edit().putFloat(KEY_PITCH_STEP, v.coerceAtLeast(0.1f)).apply()

    var defaultSpeedStep: Float
        get() = p.getFloat(KEY_SPEED_STEP, 0.1f)
        set(v) = p.edit().putFloat(KEY_SPEED_STEP, v.coerceAtLeast(0.01f)).apply()

    var defaultTrimStepMs: Long
        get() = p.getLong(KEY_TRIM_STEP_MS, 10000L)
        set(v) = p.edit().putLong(KEY_TRIM_STEP_MS, v.coerceAtLeast(1000L)).apply()

    var defaultVolumeStepPercent: Int
        get() = p.getInt(KEY_VOLUME_STEP, 5)
        set(v) = p.edit().putInt(KEY_VOLUME_STEP, v.coerceIn(1, 100)).apply()

    var defaultEnhancement: EnhancementMode
        get() = EnhancementMode.fromKey(p.getString(KEY_ENHANCEMENT, null))
        set(v) = p.edit().putString(KEY_ENHANCEMENT, v.storageKey).apply()

    companion object {
        private const val PREFS = "crow_theatron_prefs"
        private const val KEY_SEEK_JUMP = "default_seek_jump_sec"
        private const val KEY_PITCH_STEP = "default_pitch_step_semitones"
        private const val KEY_SPEED_STEP = "default_speed_step"
        private const val KEY_TRIM_STEP_MS = "default_trim_step_ms"
        private const val KEY_VOLUME_STEP = "default_volume_step_percent"
        private const val KEY_ENHANCEMENT = "default_enhancement"
    }
}
