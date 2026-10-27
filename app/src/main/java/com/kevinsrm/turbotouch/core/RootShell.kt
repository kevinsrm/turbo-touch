package com.kevinsrm.turbotouch.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Execucao de comandos de shell, com ou sem root.
 *
 * A leitura de stdout e stderr acontece em um unico stream (redirectErrorStream),
 * assim nao existe risco de deadlock quando o processo escreve muito em stderr.
 */
class RootShell {

    @Volatile
    private var rootState: Boolean? = null

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
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
        val result = withContext(Dispatchers.IO) { execRaw(command, asRoot = true) }
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
