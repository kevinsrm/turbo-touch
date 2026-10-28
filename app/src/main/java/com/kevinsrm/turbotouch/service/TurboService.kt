package com.kevinsrm.turbotouch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.kevinsrm.turbotouch.AppGraph
import com.kevinsrm.turbotouch.R
import com.kevinsrm.turbotouch.TurboApp
import com.kevinsrm.turbotouch.profile.RefreshTarget
import com.kevinsrm.turbotouch.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servico em primeiro plano que vigia o app em primeiro plano e aplica o
 * perfil de hardware configurado.
 */
class TurboService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var graph: AppGraph

    private lateinit var overlay: MonitorOverlay
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayTick = object : Runnable {
        override fun run() {
            overlay.update()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    private var lastGlobalRefresh: Int = -1
    private var lastLockClocks: Boolean? = null
    private var lastThermal: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        graph = (application as TurboApp).graph
        overlay = MonitorOverlay(this, graph.prefs)
        mainHandler.post(overlayTick)
        createChannel()
        startInForeground()
        scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        mainHandler.removeCallbacks(overlayTick)
        overlay.close()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            try {
                val requestedMode = graph.prefs.executionMode
                if (requestedMode != graph.shell.mode) {
                    // Reverta usando o modo anterior antes de trocar as credenciais.
                    graph.engine.revertEverything()
                    if (lastLockClocks == true) graph.power.setFixedPerformance(false)
                    if (lastThermal == true) graph.power.setThermalOverride(false)
                    graph.refresh.restore()
                    graph.shell.mode = requestedMode
                    lastGlobalRefresh = -1
                    lastLockClocks = null
                    lastThermal = null
                }
                val root = graph.shell.hasRoot()
                val packageName = graph.foreground.currentPackage(root)
                if (packageName != null) {
                    graph.prefs.lastForegroundPackage = packageName
                    graph.engine.onForegroundChanged(packageName)
                }
                applyGlobals()
            } catch (t: Throwable) {
                // um comando quebrado nunca derruba o loop
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun applyGlobals() {
        val prefs = graph.prefs

        val refresh = prefs.globalRefreshHz
        if (refresh != lastGlobalRefresh) {
            lastGlobalRefresh = refresh
            if (refresh <= 0) {
                graph.refresh.restore()
            } else {
                graph.refresh.apply(RefreshTarget.fromHz(refresh))
            }
        }

        val lock = prefs.lockClocksGlobal
        if (lock != lastLockClocks) {
            lastLockClocks = lock
            graph.power.setFixedPerformance(lock)
        }

        val thermal = prefs.thermalOverride
        if (thermal != lastThermal) {
            lastThermal = thermal
            graph.power.setThermalOverride(thermal)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_turbo)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "turbo_monitor"
        private const val NOTIFICATION_ID = 4201
        private const val POLL_INTERVAL_MS = 900L
        const val ACTION_STOP = "com.kevinsrm.turbotouch.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TurboService::class.java)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // Android 15 bloqueia inicio de FGS em alguns momentos do sistema
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TurboService::class.java))
        }
    }
}
