package com.kevinsrm.turbotouch.profile

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.core.ShellResult
import kotlin.math.abs

/**
 * Controle da taxa de atualizacao do painel.
 *
 * O Android nao expoe API publica para fixar refresh por app: o que existe e o
 * modo de display preferido (global) mais a politica de content frame rate.
 * A estrategia do app e:
 *
 *   1. escolhe o modo real do painel (DisplayManager.supportedModes) e fixa com
 *      `cmd display set-user-preferred-display-mode` (root);
 *   2. ajusta min/peak_refresh_rate (root, ou API Settings com WRITE_SETTINGS);
 *   3. desliga o downswitch por conteudo (root) para o painel nao voltar a 60 Hz
 *      quando o app desenha devagar.
 *
 * O motor entra e sai do modo conforme o app monitorado entra/sai de primeiro plano.
 */
class RefreshRateController(
    private val context: Context,
    private val shell: RootShell
) {

    data class Mode(val width: Int, val height: Int, val hz: Float)

    fun supportedModes(): List<Mode> {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        return display.supportedModes
            .map { Mode(it.physicalWidth, it.physicalHeight, it.refreshRate) }
            .distinctBy { Triple(it.width, it.height, it.hz) }
            .sortedBy { it.hz }
    }

    fun currentMode(): Mode? {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return null
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = display.mode
        return Mode(mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
    }

    suspend fun apply(target: RefreshTarget): List<ShellResult> {
        if (target == RefreshTarget.AUTO) return restore()
        val hz = target.hz.toFloat()
        val results = mutableListOf<ShellResult>()

        if (shell.hasRoot()) {
            val mode = supportedModes()
                .filter { it.hz > 1f }
                .minByOrNull { abs(it.hz - hz) }
            if (mode != null) {
                results += shell.exec(
                    "cmd display set-user-preferred-display-mode " +
                        "${mode.width} ${mode.height} ${mode.hz}"
                )
            }
            results += shell.exec("settings put secure match_content_frame_rate 0")
            results += shell.exec("cmd display set-match-content-frame-rate-pref never")
        }

        results += writeSystemRefresh(hz, hz)
        return results
    }

    suspend fun restore(): List<ShellResult> {
        val results = mutableListOf<ShellResult>()

        if (canWriteSettings()) {
            Settings.System.putFloat(context.contentResolver, "min_refresh_rate", 0f)
            Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", 0f)
            results += ShellResult(0, "min/peak_refresh_rate devolvidos ao automatico", "")
        }

        if (shell.hasRoot()) {
            results += shell.exec("cmd display clear-user-preferred-display-mode")
            results += shell.exec("settings delete system min_refresh_rate")
            results += shell.exec("settings delete system peak_refresh_rate")
            results += shell.exec("settings put secure match_content_frame_rate 1")
        }

        return results
    }

    private fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    private suspend fun writeSystemRefresh(minHz: Float, peakHz: Float): ShellResult {
        if (canWriteSettings()) {
            Settings.System.putFloat(context.contentResolver, "min_refresh_rate", minHz)
            Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", peakHz)
            return ShellResult(0, "Settings.System refresh $minHz/$peakHz", "")
        }
        return shell.exec(
            "settings put system min_refresh_rate $minHz; " +
                "settings put system peak_refresh_rate $peakHz"
        )
    }
}
