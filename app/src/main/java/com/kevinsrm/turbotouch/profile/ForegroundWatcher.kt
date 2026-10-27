package com.kevinsrm.turbotouch.profile

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.kevinsrm.turbotouch.core.RootShell

/**
 * Descobre qual app esta em primeiro plano.
 *
 * Com root: dumpsys activity activities (rapido, sempre correto).
 * Sem root: UsageStatsManager (exige "acesso a dados de uso" concedido pelo usuario).
 */
class ForegroundWatcher(
    private val context: Context,
    private val shell: RootShell
) {

    private val packagePattern = Regex("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/")

    suspend fun currentPackage(rootAvailable: Boolean): String? {
        if (rootAvailable) {
            val dump = shell.exec(
                "dumpsys activity activities | grep -E " +
                    "'mResumedActivity|topResumedActivity|mFocusedApp' | head -n 3"
            )
            packagePattern.find(dump.stdout)?.let { return it.groupValues[1] }
        }
        return currentFromUsageStats()
    }

    private fun currentFromUsageStats(): String? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 10_000L, now)
        var last: String? = null
        while (events.hasNextEvent()) {
            val event = events.nextEvent()
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }
}
