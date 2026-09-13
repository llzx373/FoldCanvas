package com.llzx373.foldcanvas.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeThemeId: String?
        get() = prefs.getString(KEY_ACTIVE_THEME, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_THEME, value).apply()

    var animationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATION_ENABLED, value).apply()

    var angleStart: Float
        get() = prefs.getFloat(KEY_ANGLE_START, DEFAULT_ANGLE_START)
        set(value) = prefs.edit().putFloat(KEY_ANGLE_START, value).apply()

    var angleEnd: Float
        get() = prefs.getFloat(KEY_ANGLE_END, DEFAULT_ANGLE_END)
        set(value) = prefs.edit().putFloat(KEY_ANGLE_END, value).apply()

    /** 铰链角度低通去抖（牺牲少量跟手性换稳定）。 */
    var smoothingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMOOTHING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMOOTHING_ENABLED, value).apply()

    /** EMA 系数：越小越平滑、延迟越大，建议 0.05~0.5。 */
    var smoothingAlpha: Float
        get() = prefs.getFloat(KEY_SMOOTHING_ALPHA, DEFAULT_SMOOTHING_ALPHA)
        set(value) = prefs.edit().putFloat(KEY_SMOOTHING_ALPHA, value).apply()

    /** 演示模式：壁纸引擎自动开合循环（录屏/无折叠机演示用）。 */
    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_MODE, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val DEFAULT_ANGLE_START = 30f
        const val DEFAULT_ANGLE_END = 150f
        const val DEFAULT_SMOOTHING_ALPHA = 0.15f
        private const val PREFS_NAME = "foldcanvas"
        private const val KEY_ACTIVE_THEME = "active_theme_id"
        private const val KEY_ANIMATION_ENABLED = "animation_enabled"
        private const val KEY_ANGLE_START = "angle_start"
        private const val KEY_ANGLE_END = "angle_end"
        private const val KEY_SMOOTHING_ENABLED = "smoothing_enabled"
        private const val KEY_SMOOTHING_ALPHA = "smoothing_alpha"
        private const val KEY_DEMO_MODE = "demo_mode"
    }
}
