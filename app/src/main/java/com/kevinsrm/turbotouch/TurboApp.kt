package com.kevinsrm.turbotouch

import android.app.Application
import android.content.Context
import com.kevinsrm.turbotouch.ads.AdSlotController
import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.data.Prefs
import com.kevinsrm.turbotouch.profile.CpuGpuController
import com.kevinsrm.turbotouch.profile.ForegroundWatcher
import com.kevinsrm.turbotouch.profile.PowerController
import com.kevinsrm.turbotouch.profile.ProfileEngine
import com.kevinsrm.turbotouch.profile.ProfileStore
import com.kevinsrm.turbotouch.profile.RefreshRateController

class TurboApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Grafo de dependencias simples: um unico lugar para resolver as pecas. */
class AppGraph(context: Context) {

    val appContext: Context = context.applicationContext
    val prefs = Prefs(appContext)
    val shell = RootShell(prefs.executionMode)
    val profiles = ProfileStore(appContext)
    val refresh = RefreshRateController(appContext, shell)
    val power = PowerController(shell)
    val cpuGpu = CpuGpuController(shell)
    val foreground = ForegroundWatcher(appContext, shell)
    val engine = ProfileEngine(refresh, power, cpuGpu, profiles)
    val ads = AdSlotController()
}
