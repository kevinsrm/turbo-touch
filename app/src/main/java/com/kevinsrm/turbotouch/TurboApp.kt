package com.kevinsrm.turbotouch

import android.app.Application
import android.content.Context
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        // Stable host identity is required: the device remembers the ADB key after pairing.
        val privateKeyFile = File(noBackupFilesDir, "adb-wireless-host-key.pem")
        KadbCert.configure(PrivateAdbKeyStore(privateKeyFile))
        graph = AppGraph(this)
    }
}

/** Grafo de dependencias simples: um unico lugar para resolver as pecas. */
class AppGraph(context: Context) {

    val appContext: Context = context.applicationContext
    val prefs = Prefs(appContext)
    val shell = RootShell(prefs.executionMode) { prefs.adbConnectionPort.takeIf { it in 1..65535 } }
    val profiles = ProfileStore(appContext)
    val refresh = RefreshRateController(appContext, shell)
    val power = PowerController(shell)
    val cpuGpu = CpuGpuController(shell)
    val foreground = ForegroundWatcher(appContext, shell)
    val engine = ProfileEngine(refresh, power, cpuGpu, profiles)
    val ads = AdSlotController()
}

/** Keeps the ADB identity stable between process restarts, outside Android backups. */
private class PrivateAdbKeyStore(private val file: File) : KadbPrivateKeyStore {
    override fun readPrivateKeyPem(): ByteArray? = if (file.isFile) file.readBytes() else null

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(privateKeyPem)
                stream.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    override fun clear() {
        if (file.exists() && !file.delete()) error("Could not remove ADB identity")
    }
}
