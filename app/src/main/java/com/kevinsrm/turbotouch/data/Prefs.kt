package com.kevinsrm.turbotouch.data

import android.content.Context

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

    /** null = usa o valor do arquivo de configuracao de anuncios. */
    var adsEnabledOverride: Boolean?
        get() = if (prefs.contains(KEY_ADS_ENABLED)) {
            prefs.getBoolean(KEY_ADS_ENABLED, true)
        } else {
            null
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_ADS_ENABLED) else editor.putBoolean(KEY_ADS_ENABLED, value)
            editor.apply()
        }

    /** Libera o harness de teste de clique (QA) do modulo de anuncios. */
    var qaClickTest: Boolean
        get() = prefs.getBoolean(KEY_QA_CLICK, false)
        set(value) = prefs.edit().putBoolean(KEY_QA_CLICK, value).apply()

    var lastForegroundPackage: String
        get() = prefs.getString(KEY_LAST_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PKG, value).apply()

    private companion object {
        const val KEY_SERVICE = "service_enabled"
        const val KEY_LOCK = "lock_clocks"
        const val KEY_THERMAL = "thermal_override"
        const val KEY_REFRESH = "global_refresh_hz"
        const val KEY_ADS_ENABLED = "ads_enabled"
        const val KEY_QA_CLICK = "qa_click_test"
        const val KEY_LAST_PKG = "last_foreground_pkg"
    }
}
