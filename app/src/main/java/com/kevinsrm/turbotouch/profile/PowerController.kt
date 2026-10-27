package com.kevinsrm.turbotouch.profile

import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.core.ShellResult

/**
 * Comandos de energia e thermal do Android.
 *
 *   cmd game mode <pkg> performance|battery|standard  -> GameManagerService (Android 12+)
 *   cmd power set-fixed-performance-mode-enabled true  -> clock maximo, sem throttling ate o limite
 *   cmd thermalservice override-status 0               -> trava o status termal reportado
 *   cmd deviceidle force-idle <pkg>                    -> adormece o app (economia agresiva)
 *   cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore  -> corta execucao em segundo plano
 */
class PowerController(private val shell: RootShell) {

    suspend fun setGameMode(packageName: String, mode: GameMode): ShellResult {
        if (mode == GameMode.DEFAULT) return ShellResult(0, "game mode inalterado", "")
        val byName = shell.exec("cmd game mode $packageName ${mode.wire}")
        if (byName.ok) return byName
        return shell.exec("cmd game mode $packageName ${mode.numeric}")
    }

    suspend fun setFixedPerformance(enabled: Boolean): ShellResult {
        return if (enabled) {
            shell.exec(
                "cmd power set-fixed-performance-mode-enabled true; " +
                    "cmd power set-fixed-performance-mode-threshold 95"
            )
        } else {
            shell.exec("cmd power reset-fixed-performance-mode-enabled")
        }
    }

    suspend fun setThermalOverride(enabled: Boolean): ShellResult {
        return if (enabled) {
            shell.exec("cmd thermalservice override-status 0")
        } else {
            shell.exec("cmd thermalservice reset")
        }
    }

    suspend fun setForceIdle(packageName: String, enabled: Boolean): ShellResult {
        return shell.exec(
            if (enabled) "cmd deviceidle force-idle $packageName"
            else "cmd deviceidle unforce-idle $packageName"
        )
    }

    suspend fun setBackgroundRestricted(packageName: String, restricted: Boolean): ShellResult {
        return shell.exec(
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND " +
                if (restricted) "ignore" else "default"
        )
    }
}
