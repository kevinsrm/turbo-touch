package com.kevinsrm.turbotouch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kevinsrm.turbotouch.TurboApp

/** Religa o monitor depois de reiniciar o aparelho. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? TurboApp ?: return
        if (!app.graph.prefs.serviceEnabled) return
        TurboService.start(context)
    }
}
