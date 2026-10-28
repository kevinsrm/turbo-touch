package com.kevinsrm.turbotouch.core

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Execucao de comandos pelo shell do sistema, root tradicional ou ADB Wireless.
 * O modo ADB nunca tenta `su`: comandos seguem como uid shell (normalmente 2000).
 */
class RootShell(
    initialMode: ExecutionMode = ExecutionMode.STANDARD,
    private val adbPortProvider: () -> Int? = { null }
) {
    @Volatile
    var mode: ExecutionMode = initialMode
        set(value) {
            field = value
            rootState = null
        }

    @Volatile
    private var rootState: Boolean? = null

    private val adbMutex = Mutex()

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        if (mode != ExecutionMode.ROOT) return@withContext false
        rootState ?: run {
            val probe = execRaw("id", asRoot = true)
            val ok = probe.ok && (probe.stdout.contains("uid=0") || probe.stdout.contains("uid=0("))
            rootState = ok
            ok
        }
    }

    fun invalidateRootCache() {
        rootState = null
    }

    suspend fun exec(command: String, record: Boolean = true): ShellResult {
        val activeMode = mode
        val result = when (activeMode) {
            ExecutionMode.ROOT -> withContext(Dispatchers.IO) { execRaw(command, asRoot = true) }
            // O monitor e o loop de perfis executam em paralelo: serializamos o ADB
            // para nao abrir duas conexoes TLS (handshake + auth) ao mesmo tempo.
            ExecutionMode.ADB_WIFI -> withContext(Dispatchers.IO) { adbMutex.withLock { execAdb(command) } }
            ExecutionMode.STANDARD -> ShellResult(-1, "", "Comando privilegiado indisponivel no modo sem root")
        }
        if (record) CommandLog.record(command, result)
        return result
    }

    suspend fun execPlain(command: String): ShellResult {
        val result = withContext(Dispatchers.IO) { execRaw(command, asRoot = false) }
        CommandLog.record(command, result)
        return result
    }

    private fun execAdb(command: String): ShellResult {
        val port = adbPortProvider()
            ?: return ShellResult(-1, "", "Informe a porta de conexão ADB após o pareamento Wi-Fi")
        return try {
            require(port in 1..65535) { "Porta ADB inválida" }
            Kadb.create("127.0.0.1", port, connectTimeout = ADB_TIMEOUT_MS, socketTimeout = ADB_TIMEOUT_MS).use { adb ->
                val response = adb.shell(command)
                ShellResult(response.exitCode, response.output, response.errorOutput)
            }
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: "Falha ao executar comando ADB local")
        }
    }

    private fun execRaw(command: String, asRoot: Boolean): ShellResult {
        return try {
            val builder = ProcessBuilder(
                if (asRoot) listOf("su", "-c", command) else listOf("sh", "-c", command)
            )
            builder.redirectErrorStream(true)
            val process = builder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            ShellResult(code, output, "")
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: "exec failed: ${t::class.java.simpleName}")
        }
    }

    companion object {
        private const val ADB_TIMEOUT_MS = 5_000
    }
}

enum class ExecutionMode { ROOT, STANDARD, ADB_WIFI }
