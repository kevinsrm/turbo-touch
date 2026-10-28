package com.kevinsrm.turbotouch.service

import android.os.SystemClock
import com.kevinsrm.turbotouch.core.CommandLog
import com.kevinsrm.turbotouch.core.ExecutionMode
import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.core.ShellResult
import com.kevinsrm.turbotouch.data.Prefs
import java.io.File

/**
 * Telemetria best-effort do aparelho para o HUD.
 *
 * CPU, GPU e FPS vem do **shell** (root ou ADB Wi-Fi, que roda como uid `shell`):
 * o proprio app nao consegue ler `/proc/stat` nem os nos sysfs da GPU desde o
 * Android 8, por isso a leitura direta de arquivos retornava `n/d`.
 *
 * Uma unica chamada de shell por amostra coleta tudo; o FPS reaproveita a camada
 * do SurfaceFlinger ja descoberta para o app em primeiro plano.
 */
class MonitorMetrics(private val shell: RootShell, private val prefs: Prefs) {

    data class Snapshot(
        val fps: String = "n/d",
        val cpu: String = "n/d",
        val gpu: String = "n/d",
        val hint: String = ""
    )

    private var previousCpu: MetricsParsers.Ticks? = null
    private var cachedLayer: String? = null
    private var cachedPackage: String = ""
    private var emptyLayerSamples: Int = 0
    private var lastDiscoveryMs: Long = 0L
    private var gfxBaseline: Pair<Long, Long>? = null
    private var lastLoggedFailure: String = ""

    suspend fun sample(): Snapshot {
        val wantCpuGpu = prefs.hudCpuGpu
        val wantFps = prefs.hudFps
        if (!wantCpuGpu && !wantFps) return Snapshot()

        val packageName = prefs.lastForegroundPackage.trim()
        if (packageName != cachedPackage) {
            cachedPackage = packageName
            cachedLayer = null
            emptyLayerSamples = 0
            lastDiscoveryMs = 0L
            gfxBaseline = null
        }

        if (!privileged()) {
            return localSnapshot(wantCpuGpu)
        }

        val layer = cachedLayer
        val discover = layer == null &&
            SystemClock.elapsedRealtime() - lastDiscoveryMs >= DISCOVERY_INTERVAL_MS
        if (discover) lastDiscoveryMs = SystemClock.elapsedRealtime()

        val result = shell.exec(buildScript(wantCpuGpu, wantFps, packageName, layer, discover), record = false)
        if (result.stdout.isBlank()) {
            noteFailure(result)
            return Snapshot(hint = shellHint())
        }
        val parsed = MetricsParsers.parseSections(result.stdout)

        val cpu = if (wantCpuGpu) cpuText(parsed.stat) else "n/d"
        val gpu = if (wantCpuGpu) MetricsParsers.gpuPercent(parsed.gpu)?.let { "$it%" } ?: "n/d" else "n/d"
        val fps = if (wantFps) fpsText(parsed, layer, discover) else "n/d"
        return Snapshot(fps = fps, cpu = cpu, gpu = gpu, hint = metricHint(wantFps, fps))
    }

    // ---------------------------------------------------------------- estado

    private suspend fun privileged(): Boolean = when (shell.mode) {
        ExecutionMode.STANDARD -> false
        ExecutionMode.ROOT -> shell.hasRoot()
        ExecutionMode.ADB_WIFI -> prefs.adbConnectionPort in 1..65535
    }

    private fun localSnapshot(wantCpuGpu: Boolean): Snapshot {
        val cpu = if (wantCpuGpu) {
            val stat = runCatching { File("/proc/stat").useLines { it.firstOrNull() } }.getOrNull()
            cpuText(stat)
        } else {
            "n/d"
        }
        val gpu = if (wantCpuGpu) localGpu() else "n/d"
        return Snapshot(fps = "n/d", cpu = cpu, gpu = gpu, hint = shellHint())
    }

    private fun cpuText(line: String?): String {
        val ticks = MetricsParsers.ticks(line) ?: return "n/d"
        val previous = previousCpu
        previousCpu = ticks
        if (previous == null) return "…"
        return MetricsParsers.cpuPercent(previous, ticks)?.let { "$it%" } ?: "n/d"
    }

    private fun localGpu(): String {
        for (path in MetricsParsers.GPU_CANDIDATES) {
            if (path.contains('*')) continue
            val text = runCatching {
                File(path).takeIf { it.isFile && it.canRead() }?.readText()
            }.getOrNull() ?: continue
            val percent = MetricsParsers.gpuPercent(path, text) ?: continue
            return "$percent%"
        }
        return "n/d"
    }

    private fun fpsText(parsed: MetricsParsers.Sections, previousLayer: String?, discovered: Boolean): String {
        val now = System.nanoTime()
        var best: Double? = null
        var bestLayer: String? = null
        for ((layer, text) in parsed.latencies) {
            val fps = MetricsParsers.latencyFps(text, now) ?: continue
            val known = best
            if (known == null || fps > known) {
                best = fps
                bestLayer = layer
            }
        }
        val measured = best
        val measuredLayer = bestLayer
        if (measured != null && measuredLayer != null) {
            cachedLayer = measuredLayer
            emptyLayerSamples = 0
            return MetricsParsers.formatFps(measured)
        }

        val frames = parsed.totalFrames
        val usesGfx = frames != null && frames > 0L

        if (previousLayer != null) {
            emptyLayerSamples += 1
            if (emptyLayerSamples >= STALE_LAYER_SAMPLES) {
                cachedLayer = null
                emptyLayerSamples = 0
            }
        } else if (discovered && !usesGfx) {
            // Guarda a camada mais provavel para nao repetir a descoberta a cada amostra.
            // Se o `gfxinfo` esta entregando frames, ele e a fonte melhor: nao fixa a camada.
            emptyLayerSamples = 0
            cachedLayer = parsed.latencies.keys
                .filter(MetricsParsers::isLayerNameSafe)
                .maxByOrNull(MetricsParsers::scoreLayer)
                ?.takeIf { MetricsParsers.scoreLayer(it) >= 0 }
        }

        if (frames != null && frames > 0L) return gfxFps(frames)
        return "n/d"
    }

    /** Fallback para camadas bloqueadas: usa o contador de frames do proprio app. */
    private fun gfxFps(total: Long): String {
        val now = SystemClock.elapsedRealtime()
        val previous = gfxBaseline
        gfxBaseline = total to now
        if (previous == null) return "…"
        val frames = total - previous.first
        val elapsed = now - previous.second
        if (frames < 0L || elapsed <= 0L || elapsed > GFX_BASELINE_MAX_AGE_MS) return "…"
        val fps = frames * 1000.0 / elapsed
        if (fps > 240.0) return "n/d"
        return MetricsParsers.formatFps(fps)
    }

    // ---------------------------------------------------------------- script

    private fun buildScript(
        wantCpuGpu: Boolean,
        wantFps: Boolean,
        packageName: String,
        layer: String?,
        discover: Boolean
    ): String {
        val script = StringBuilder()
        if (wantCpuGpu) {
            script.append("printf '%s\\n' '${MetricsParsers.MARK_STAT}'; ")
            script.append("head -n 1 /proc/stat 2>/dev/null; ")
            script.append("printf '%s\\n' '${MetricsParsers.MARK_GPU}'; ")
            script.append("for f in")
            MetricsParsers.GPU_CANDIDATES.forEach { path -> script.append(' ').append(path) }
            script.append("; do ")
            script.append("if [ -r \"\$f\" ]; then ")
            script.append("printf '%s\\n' \"${MetricsParsers.MARK_GPU_FILE}\$f\"; ")
            script.append("cat \"\$f\" 2>/dev/null; ")
            script.append("fi; done; ")
            script.append("for d in /sys/class/devfreq/*; do ")
            script.append("case \"\$d\" in ")
            script.append("*gpu*|*GPU*|*mali*|*kgsl*) ")
            script.append("if [ -r \"\$d/load\" ]; then ")
            script.append("printf '%s\\n' \"${MetricsParsers.MARK_GPU_FILE}\$d/load\"; ")
            script.append("cat \"\$d/load\" 2>/dev/null; ")
            script.append("fi;; ")
            script.append("esac; done; ")
        }

        val safePackage = packageName.takeIf { it.matches(PACKAGE_PATTERN) }
        if (wantFps) {
            script.append("printf '%s\\n' '${MetricsParsers.MARK_FPS}'; ")
            if (layer != null && MetricsParsers.isLayerNameSafe(layer)) {
                script.append("printf '%s\\n' ")
                    .append(MetricsParsers.singleQuote(MetricsParsers.MARK_LATENCY + layer))
                    .append("; ")
                script.append("dumpsys SurfaceFlinger --latency ")
                    .append(MetricsParsers.singleQuote(layer))
                    .append(" 2>/dev/null; ")
            } else if (safePackage != null) {
                if (discover) {
                    script.append("dumpsys SurfaceFlinger --list 2>/dev/null | tr -d '\\r' | grep -F -- ")
                        .append(MetricsParsers.singleQuote(safePackage))
                        .append(" | head -n 6 | while IFS= read -r l; do ")
                    script.append("[ -n \"\$l\" ] || continue; ")
                    script.append("printf '%s\\n' \"${MetricsParsers.MARK_LATENCY}\$l\"; ")
                    script.append("dumpsys SurfaceFlinger --latency \"\$l\" 2>/dev/null; ")
                    script.append("done; ")
                }
                script.append("printf '%s\\n' '${MetricsParsers.MARK_GFX}'; ")
                script.append("dumpsys gfxinfo ")
                    .append(MetricsParsers.singleQuote(safePackage))
                    .append(" 2>/dev/null | grep -m 1 -F 'Total frames rendered:'; ")
            } else {
                // Sem pacote conhecido: em versoes antigas o buffer vem sem argumento.
                script.append("dumpsys SurfaceFlinger --latency 2>/dev/null; ")
            }
        }

        script.append("printf '%s\\n' '${MetricsParsers.MARK_END}'")
        return script.toString()
    }

    // ---------------------------------------------------------------- avisos

    private fun shellHint(): String = when (shell.mode) {
        ExecutionMode.STANDARD -> "FPS, CPU e GPU exigem Root ou ADB Wi-Fi."
        ExecutionMode.ROOT -> "Root não respondeu; métricas limitadas."
        ExecutionMode.ADB_WIFI ->
            if (prefs.adbConnectionPort !in 1..65535) "Informe a porta de conexão ADB em Ajustes."
            else "ADB Wi-Fi sem conexão; confira porta e pareamento."
    }

    private fun metricHint(wantFps: Boolean, fps: String): String {
        if (wantFps && fps == "n/d") return "FPS: camada do app não exposta pelo SurfaceFlinger."
        return ""
    }

    private fun noteFailure(result: ShellResult) {
        val message = result.combined().ifBlank { "falha ao executar o comando de métricas" }
        if (message == lastLoggedFailure) return
        lastLoggedFailure = message
        CommandLog.record("monitor: métricas (${shell.mode.name})", ShellResult(-1, "", message))
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{1,200}")
        const val DISCOVERY_INTERVAL_MS = 5_000L
        const val STALE_LAYER_SAMPLES = 3
        const val GFX_BASELINE_MAX_AGE_MS = 10_000L
    }
}
