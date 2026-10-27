package com.kevinsrm.turbotouch.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistencia dos perfis em SharedPreferences (JSON). */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("turbo_profiles", Context.MODE_PRIVATE)

    fun all(): Map<String, AppProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyMap()
        return try {
            val array = JSONArray(raw)
            val result = LinkedHashMap<String, AppProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName")
                if (pkg.isNotBlank()) result[pkg] = obj.toProfile()
            }
            result
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    fun get(packageName: String): AppProfile? = all()[packageName]

    fun upsert(profile: AppProfile) {
        val current = all().toMutableMap()
        current[profile.packageName] = profile
        write(current)
    }

    fun remove(packageName: String) {
        val current = all().toMutableMap()
        current.remove(packageName)
        write(current)
    }

    private fun write(profiles: Map<String, AppProfile>) {
        val array = JSONArray()
        profiles.values.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun AppProfile.toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("refreshHz", refresh.hz)
        put("gameMode", gameMode.name)
        put("lockMaxClocks", lockMaxClocks)
        put("aggressiveSaver", aggressiveSaver)
        put("gpuBoost", gpuBoost)
        put("schedulerBoost", schedulerBoost)
        put("enabled", enabled)
    }

    private fun JSONObject.toProfile(): AppProfile = AppProfile(
        packageName = optString("packageName"),
        appLabel = optString("appLabel"),
        refresh = RefreshTarget.fromHz(optInt("refreshHz", 0)),
        gameMode = GameMode.fromWire(optString("gameMode")),
        lockMaxClocks = optBoolean("lockMaxClocks"),
        aggressiveSaver = optBoolean("aggressiveSaver"),
        gpuBoost = optBoolean("gpuBoost"),
        schedulerBoost = optBoolean("schedulerBoost"),
        enabled = optBoolean("enabled", true)
    )

    private companion object {
        const val KEY_PROFILES = "profiles"
    }
}
