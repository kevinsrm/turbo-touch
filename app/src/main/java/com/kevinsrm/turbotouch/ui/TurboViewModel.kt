package com.kevinsrm.turbotouch.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinsrm.turbotouch.AppGraph
import com.kevinsrm.turbotouch.TurboApp
import com.kevinsrm.turbotouch.ads.AdConfig
import com.kevinsrm.turbotouch.core.CommandEntry
import com.kevinsrm.turbotouch.core.CommandLog
import com.kevinsrm.turbotouch.data.InstalledApp
import com.kevinsrm.turbotouch.data.InstalledApps
import com.kevinsrm.turbotouch.profile.AppProfile
import com.kevinsrm.turbotouch.service.TurboService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TurboUiState(
    val rootAvailable: Boolean = false,
    val serviceEnabled: Boolean = true,
    val foregroundPackage: String = "",
    val foregroundLabel: String = "",
    val currentHz: Float = 0f,
    val supportedHz: List<Float> = emptyList(),
    val activeProfile: AppProfile? = null,
    val profiles: Map<String, AppProfile> = emptyMap(),
    val installedApps: List<InstalledApp> = emptyList(),
    val log: List<CommandEntry> = emptyList(),
    val adsConfig: AdConfig = AdConfig(),
    val adsEnabled: Boolean = false,
    val globalRefreshHz: Int = 0,
    val lockClocks: Boolean = false,
    val thermalOverride: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val canWriteSettings: Boolean = false,
    val message: String = ""
)

class TurboViewModel(app: Application) : AndroidViewModel(app) {

    private val graph: AppGraph = (app as TurboApp).graph

    private val _state = MutableStateFlow(TurboUiState())
    val state: StateFlow<TurboUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(2000)
            }
        }
    }

    suspend fun refresh() {
        val root = graph.shell.hasRoot()
        val packageName = graph.foreground.currentPackage(root)
        val apps = currentApps()
        val label = packageName?.let { pkg ->
            apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
        } ?: ""

        _state.value = _state.value.copy(
            rootAvailable = root,
            serviceEnabled = graph.prefs.serviceEnabled,
            foregroundPackage = packageName.orEmpty(),
            foregroundLabel = label,
            currentHz = graph.refresh.currentMode()?.hz ?: 0f,
            supportedHz = graph.refresh.supportedModes().map { it.hz }.distinct().sorted(),
            activeProfile = graph.engine.activeProfile,
            profiles = graph.profiles.all(),
            installedApps = apps,
            log = CommandLog.entries.value,
            adsConfig = graph.ads.currentConfig(),
            adsEnabled = graph.ads.isEnabled(),
            globalRefreshHz = graph.prefs.globalRefreshHz,
            lockClocks = graph.prefs.lockClocksGlobal,
            thermalOverride = graph.prefs.thermalOverride,
            usageAccessGranted = hasUsageAccess(),
            canWriteSettings = Settings.System.canWrite(getApplication())
        )
    }

    private suspend fun currentApps(): List<InstalledApp> {
        val cached = _state.value.installedApps
        if (cached.isNotEmpty()) return cached
        return withContext(Dispatchers.IO) { InstalledApps.list(getApplication()) }
    }

    fun reloadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.list(getApplication()) }
            _state.value = _state.value.copy(installedApps = apps)
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        graph.prefs.serviceEnabled = enabled
        val context = getApplication<Application>()
        if (enabled) TurboService.start(context) else TurboService.stop(context)
        _state.value = _state.value.copy(serviceEnabled = enabled)
    }

    fun setGlobalRefresh(hz: Int) {
        graph.prefs.globalRefreshHz = hz
        _state.value = _state.value.copy(globalRefreshHz = hz)
    }

    fun setLockClocks(enabled: Boolean) {
        graph.prefs.lockClocksGlobal = enabled
        _state.value = _state.value.copy(lockClocks = enabled)
    }

    fun setThermalOverride(enabled: Boolean) {
        graph.prefs.thermalOverride = enabled
        _state.value = _state.value.copy(thermalOverride = enabled)
    }

    fun saveProfile(profile: AppProfile) {
        graph.profiles.upsert(profile)
        viewModelScope.launch { refresh() }
    }

    fun removeProfile(packageName: String) {
        graph.profiles.remove(packageName)
        viewModelScope.launch { refresh() }
    }

    fun reloadAds() {
        val config = graph.ads.reload()
        _state.value = _state.value.copy(adsConfig = config, adsEnabled = graph.ads.isEnabled())
    }

    fun saveAdsConfig(config: AdConfig) {
        AdConfig.saveOverride(getApplication(), config.asProperties())
        reloadAds()
    }

    fun setAdsEnabled(enabled: Boolean) {
        graph.prefs.adsEnabledOverride = enabled
        reloadAds()
    }

    fun setQaClickTest(enabled: Boolean) {
        graph.prefs.qaClickTest = enabled
    }

    fun clearLog() {
        CommandLog.clear()
        _state.value = _state.value.copy(log = emptyList())
    }

    fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val app = getApplication<Application>()
        val mode = try {
            val manager = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            manager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                app.packageName
            )
        } catch (t: Throwable) {
            AppOpsManager.MODE_DEFAULT
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            app.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
