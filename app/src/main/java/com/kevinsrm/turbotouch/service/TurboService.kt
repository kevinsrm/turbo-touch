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
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.flyfishxu.kadb.Kadb
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
import kotlinx.coroutines.withContext

/**
 * Servico em primeiro plano que vigia o app em primeiro plano e aplica o
 * perfil de hardware configurado.
 */
class TurboService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var graph: AppGraph

    private lateinit var overlay: MonitorOverlay

    private var pendingPairCode: String? = null
    private var pendingPairPort: Int? = null
    private var notificationStatus: String? = null

    private var lastGlobalRefresh: Int = -1
    private var lastLockClocks: Boolean? = null
    private var lastThermal: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        graph = (application as TurboApp).graph
        overlay = MonitorOverlay(this, graph.prefs)
        createChannel()
        startInForeground()
        scope.launch { monitorLoop() }
        scope.launch {
            val metrics = MonitorMetrics(graph.shell, graph.prefs)
            while (isActive) {
                val snapshot = runCatching { metrics.sample() }.getOrElse { error ->
                    MonitorMetrics.Snapshot(hint = "falha ao ler métricas: ${error.message ?: error::class.java.simpleName}")
                }
                withContext(Dispatchers.Main) { overlay.update(snapshot) }
                delay(2000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAIR_CODE -> {
                pendingPairCode = intent.getStringExtra(EXTRA_REMOTE_VALUE)?.trim()
                acceptPairInput()
            }
            ACTION_PAIR_PORT -> {
                pendingPairPort = intent.getStringExtra(EXTRA_REMOTE_VALUE)?.toIntOrNull()
                acceptPairInput()
            }
            ACTION_SHOW_PAIRING -> {
                notificationStatus = "Digite código de 6 dígitos e porta de pareamento nas ações abaixo."
                showPairingNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        overlay.close()
        // Encerra a sessao ADB persistente: sem isso o aviso "Depuracao por Wi-Fi
        // conectada" ficaria ativo depois de parar o monitor.
        graph.shell.releaseAdb()
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
                // Com o monitor desligado a sessao ADB fica ociosa e e liberada,
                // fazendo o aviso do sistema desaparecer.
                graph.shell.releaseAdbIfIdle()
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
        val pairingChannel = NotificationChannel(
            PAIRING_CHANNEL_ID,
            "Pareamento ADB Wi-Fi",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Entrada interativa do código e porta temporários do ADB Wireless"
            setShowBadge(false)
        }
        manager.createNotificationChannel(pairingChannel)
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
            .setContentText(notificationStatus ?: getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_turbo)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun pairInputAction(action: String, remoteKey: String, label: String, requestCode: Int): NotificationCompat.Action {
        val reply = RemoteInput.Builder(remoteKey).setLabel(label).build()
        val pending = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, AdbPairInputReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_turbo, label, pending)
            .addRemoteInput(reply)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        if (notificationStatus != null) showPairingNotification()
    }

    private fun showPairingNotification() {
        val notification = NotificationCompat.Builder(this, PAIRING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_turbo)
            .setContentTitle("Pareamento ADB Wi-Fi")
            .setContentText(notificationStatus)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(pairInputAction(ACTION_PAIR_CODE, REMOTE_CODE, "Código de 6 dígitos", 4202))
            .addAction(pairInputAction(ACTION_PAIR_PORT, REMOTE_PORT, "Porta de pareamento", 4203))
            .build()
        getSystemService(NotificationManager::class.java)?.notify(PAIRING_NOTIFICATION_ID, notification)
    }

    private fun acceptPairInput() {
        val code = pendingPairCode
        val port = pendingPairPort
        if (code == null || port == null) {
            notificationStatus = if (code == null) "Informe o código de pareamento de 6 dígitos."
                else "Informe a porta mostrada no diálogo de pareamento Wi-Fi."
            updateNotification()
            return
        }
        pendingPairCode = null
        pendingPairPort = null
        if (!code.matches(Regex("\\d{6}")) || port !in 1..65535) {
            notificationStatus = "Código ou porta inválidos. Digite-os novamente nas ações da notificação."
            updateNotification()
            return
        }
        notificationStatus = "Pareando com 127.0.0.1:$port…"
        updateNotification()
        scope.launch {
            val outcome = runCatching { Kadb.pair("127.0.0.1", port, code) }
            notificationStatus = if (outcome.isSuccess) {
                "Pareamento ADB concluído. Configure a porta de conexão em Ajustes."
            } else {
                "Falha no pareamento. Confira Depuração Wi-Fi, código e porta."
            }
            updateNotification()
        }
    }

    companion object {
        private const val CHANNEL_ID = "turbo_monitor"
        private const val PAIRING_CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 4201
        private const val PAIRING_NOTIFICATION_ID = 4202
        private const val POLL_INTERVAL_MS = 900L
        const val ACTION_STOP = "com.kevinsrm.turbotouch.STOP"
        const val ACTION_SHOW_PAIRING = "com.kevinsrm.turbotouch.SHOW_ADB_PAIRING"
        const val ACTION_PAIR_CODE = "com.kevinsrm.turbotouch.PAIR_CODE"
        const val ACTION_PAIR_PORT = "com.kevinsrm.turbotouch.PAIR_PORT"
        const val REMOTE_CODE = "adb_pair_code"
        const val REMOTE_PORT = "adb_pair_port"
        const val EXTRA_REMOTE_VALUE = "remote_value"

        fun requestPairing(context: Context) {
            val intent = Intent(context, TurboService::class.java).setAction(ACTION_SHOW_PAIRING)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                // The user can reopen the app and retry if Android blocks FGS startup.
            }
        }

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
