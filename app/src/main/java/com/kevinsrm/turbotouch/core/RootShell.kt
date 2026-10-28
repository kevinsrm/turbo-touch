package com.kevinsrm.turbotouch.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Execucao de comandos de shell, com ou sem root.
 *
 * A leitura de stdout e stderr acontece em um unico stream (redirectErrorStream),
 * assim nao existe risco de deadlock quando o processo escreve muito em stderr.
 */
class RootShell(@Volatile var mode: ExecutionMode = ExecutionMode.STANDARD) {

    @Volatile
    private var rootState: Boolean? = null

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

    suspend fun exec(command: String): ShellResult {
        val result = if (mode == ExecutionMode.ROOT) {
            withContext(Dispatchers.IO) { execRaw(command, asRoot = true) }
        } else {
            ShellResult(-1, "", "Comando privilegiado indisponivel no modo sem root")
        }
        CommandLog.record(command, result)
        return result
    }

    suspend fun execPlain(command: String): ShellResult {
        val result = withContext(Dispatchers.IO) { execRaw(command, asRoot = false) }
        CommandLog.record(command, result)
        return result
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
}

/** ADB Wi-Fi nao esta implementado; nunca executar comandos privilegiados como fallback. */
enum class ExecutionMode { ROOT, STANDARD }
