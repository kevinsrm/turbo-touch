package com.kevinsrm.turbotouch.core

import android.os.SystemClock
import com.flyfishxu.kadb.Kadb
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Execucao de comandos pelo shell do sistema, root tradicional ou ADB Wireless.
 * O modo ADB nunca tenta `su`: comandos seguem como uid shell (normalmente 2000).
 *
 * A conexao ADB e **persistente** por porta. Abrir um cliente novo a cada comando
 * (como antes) fazia o `adbd` anunciar "Depuracao por Wi-Fi conectada" e retirar a
 * notificacao a cada handshake — o monitor de FPS roda a cada 2 s, entao o historico
 * de notificacoes enchia de avisos piscando.
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
            releaseAdb()
        }

    @Volatile
    private var rootState: Boolean? = null

    private val adbMutex = Mutex()

    /** Cliente ADB reaproveitado entre comandos; recriado so quando a porta muda. */
    private var adbClient: Kadb? = null
    private var adbClientPort: Int = 0
    private var lastAdbUseMs: Long = 0L
    private var lastAdbConnectMs: Long = 0L

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

    /** Encerra a conexao ADB persistente (servico parado, troca de modo ou ociosa). */
    fun releaseAdb() {
        val client = adbClient
        adbClient = null
        adbClientPort = 0
        lastAdbUseMs = 0L
        if (client != null) runCatching { client.close() }
    }

    /**
     * Fecha a sessao ADB quando ninguem pediu comando por [idleMs]. Sem isso o
     * aviso "Depuracao por Wi-Fi conectada" ficaria preso na barra mesmo com o
     * monitor desligado.
     */
    fun releaseAdbIfIdle(idleMs: Long = ADB_IDLE_RELEASE_MS) {
        if (adbClient == null) return
        val idle = SystemClock.elapsedRealtime() - lastAdbUseMs
        if (idle < idleMs) return
        if (!adbMutex.tryLock()) return
        try {
            if (SystemClock.elapsedRealtime() - lastAdbUseMs >= idleMs) releaseAdb()
        } finally {
            adbMutex.unlock()
        }
    }

    private fun execAdb(command: String): ShellResult {
        val port = adbPortProvider()
            ?: return ShellResult(-1, "", "Informe a porta de conexão ADB após o pareamento Wi-Fi")
        if (port !in 1..65535) return ShellResult(-1, "", "Porta ADB inválida")

        val first = runCatching { sendAdb(port, command) }
        first.getOrNull()?.let { return it }

        val error = first.exceptionOrNull()
        if (error !is IOException && error !is IllegalStateException) {
            return ShellResult(-1, "", error?.message ?: "Falha ao executar comando ADB local")
        }
        // Falha que se repete a cada amostra nao pode virar reconexao a cada 2 s:
        // seria o mesmo pisca-pisca da notificacao do sistema.
        if (SystemClock.elapsedRealtime() - lastAdbConnectMs < MIN_RECONNECT_INTERVAL_MS) {
            return ShellResult(-1, "", error?.message ?: "Falha ao executar comando ADB local")
        }

        // O transporte pode ter caido entre dois comandos (tela apagada, adbd reiniciado).
        // Descarta o cliente e tenta uma unica vez com uma conexao nova.
        releaseAdb()
        return runCatching { sendAdb(port, command) }.getOrElse { failure ->
            ShellResult(-1, "", failure.message ?: "Falha ao executar comando ADB local")
        }
    }

    private fun sendAdb(port: Int, command: String): ShellResult {
        val response = adbClientFor(port).shell(command)
        lastAdbUseMs = SystemClock.elapsedRealtime()
        return ShellResult(response.exitCode, response.output, response.errorOutput)
    }

    private fun adbClientFor(port: Int): Kadb {
        val current = adbClient
        if (current != null && adbClientPort == port) return current
        if (current != null) runCatching { current.close() }
        adbClient = null
        adbClientPort = port
        lastAdbUseMs = SystemClock.elapsedRealtime()
        lastAdbConnectMs = lastAdbUseMs
        return Kadb.create(
            "127.0.0.1",
            port,
            connectTimeout = ADB_CONNECT_TIMEOUT_MS,
            socketTimeout = ADB_IO_TIMEOUT_MS
        ).also { adbClient = it }
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
        private const val ADB_CONNECT_TIMEOUT_MS = 5_000
        private const val ADB_IO_TIMEOUT_MS = 5_000

        /** Tempo sem comandos ate liberar a sessao ADB (some o aviso persistente). */
        const val ADB_IDLE_RELEASE_MS = 60_000L

        /** Intervalo minimo entre reconexoes automaticas apos falha de transporte. */
        private const val MIN_RECONNECT_INTERVAL_MS = 15_000L
    }
}

enum class ExecutionMode { ROOT, STANDARD, ADB_WIFI }
