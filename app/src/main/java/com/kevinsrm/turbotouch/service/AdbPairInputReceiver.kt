package com.kevinsrm.turbotouch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/** Forwards notification inline replies to the existing foreground service. */
class AdbPairInputReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = when (intent.action) {
            TurboService.ACTION_PAIR_CODE -> TurboService.REMOTE_CODE
            TurboService.ACTION_PAIR_PORT -> TurboService.REMOTE_PORT
            else -> return
        }
        val value = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(key)?.toString()?.trim()
            ?: return
        val serviceIntent = Intent(context, TurboService::class.java)
            .setAction(intent.action)
            .putExtra(TurboService.EXTRA_REMOTE_VALUE, value)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
