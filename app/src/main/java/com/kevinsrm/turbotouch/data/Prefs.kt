package com.kevinsrm.turbotouch.data

import android.content.Context
import com.kevinsrm.turbotouch.core.ExecutionMode

/** Preferencias globais do app (fora dos perfis por aplicativo). */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("turbo_settings", Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE, value).apply()

    var lockClocksGlobal: Boolean
        get() = prefs.getBoolean(KEY_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK, value).apply()

    var thermalOverride: Boolean
        get() = prefs.getBoolean(KEY_THERMAL, false)
        set(value) = prefs.edit().putBoolean(KEY_THERMAL, value).apply()

    var globalRefreshHz: Int
        get() = prefs.getInt(KEY_REFRESH, 0)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    var executionMode: ExecutionMode
        get() = runCatching { ExecutionMode.valueOf(prefs.getString("execution_mode", "STANDARD") ?: "STANDARD") }
            .getOrDefault(ExecutionMode.STANDARD)
        set(value) = prefs.edit().putString("execution_mode", value.name).apply()

    var adbConnectionPort: Int
        get() = prefs.getInt("adb_connection_port", 0)
        set(value) = prefs.edit().putInt("adb_connection_port", value.coerceIn(0, 65535)).apply()

    var setupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    var hudFps: Boolean
        get() = prefs.getBoolean("hud_fps", false)
        set(value) = prefs.edit().putBoolean("hud_fps", value).apply()

    var hudCpuGpu: Boolean
        get() = prefs.getBoolean("hud_cpu_gpu", false)
        set(value) = prefs.edit().putBoolean("hud_cpu_gpu", value).apply()

    var hudRefresh: Boolean
        get() = prefs.getBoolean("hud_refresh", false)
        set(value) = prefs.edit().putBoolean("hud_refresh", value).apply()
    var hudBattery: Boolean
        get() = prefs.getBoolean("hud_battery", false)
        set(value) = prefs.edit().putBoolean("hud_battery", value).apply()

    var lastForegroundPackage: String
        get() = prefs.getString(KEY_LAST_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PKG, value).apply()

    private companion object {
        const val KEY_SERVICE = "service_enabled"
        const val KEY_LOCK = "lock_clocks"
        const val KEY_THERMAL = "thermal_override"
        const val KEY_REFRESH = "global_refresh_hz"
        const val KEY_LAST_PKG = "last_foreground_pkg"
    }
}
