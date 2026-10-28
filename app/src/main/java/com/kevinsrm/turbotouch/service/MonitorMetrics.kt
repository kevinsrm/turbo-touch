package com.kevinsrm.turbotouch.service

import com.kevinsrm.turbotouch.core.ExecutionMode
import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.data.Prefs
import java.io.File

/** Best-effort device telemetry; vendor-specific values are explicitly marked unavailable. */
class MonitorMetrics(private val shell: RootShell, private val prefs: Prefs) {
    data class Snapshot(val fps: String = "—", val cpu: String = "—", val gpu: String = "—")

    private var previousCpu: Pair<Long, Long>? = null

    suspend fun sample(): Snapshot {
        val cpu = if (prefs.hudCpuGpu) readCpuPercent() else "—"
        val gpu = if (prefs.hudCpuGpu) readGpuPercent() else "—"
        val fps = if (prefs.hudFps) readFps() else "—"
        return Snapshot(fps = fps, cpu = cpu, gpu = gpu)
    }

    private fun readCpuPercent(): String {
        return runCatching {
            val values = File("/proc/stat").useLines { lines -> lines.first().trim().split(Regex("\\s+")) }
            if (values.firstOrNull() != "cpu" || values.size < 5) return "n/d"
            val ticks = values.drop(1).mapNotNull(String::toLongOrNull).take(8)
            if (ticks.size < 4) return "n/d"
            val total = ticks.sum()
            val idle = ticks[3] + (ticks.getOrNull(4) ?: 0L)
            val now = total to idle
            val old = previousCpu
            previousCpu = now
            if (old == null) return "…"
            val totalDelta = total - old.first
            val idleDelta = idle - old.second
            if (totalDelta <= 0) "n/d" else "${(((totalDelta - idleDelta).toDouble() / totalDelta) * 100).toInt().coerceIn(0, 100)}%"
        }.getOrDefault("n/d")
    }

    private fun readGpuPercent(): String {
        val candidates = listOf(
            File("/sys/class/kgsl/kgsl-3d0/gpubusy"),
            File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"),
            File("/sys/devices/platform/1c40000.gpu/utilisation"),
            File("/sys/devices/platform/13000000.mali/utilisation")
        )
        for (file in candidates) {
            if (!file.exists() || !file.isFile) continue
            val text = runCatching { file.readText().trim() }.getOrNull().orEmpty()
            val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toLongOrNull() }.toList()
            val percent = when {
                text.contains("%") -> numbers.firstOrNull()?.toInt()
                numbers.size >= 2 && numbers[1] > 0 -> (numbers[0] * 100L / numbers[1]).toInt()
                numbers.size == 1 && numbers[0] in 0..100 -> numbers[0].toInt()
                else -> null
            }
            if (percent != null && percent in 0..100) return "$percent%"
        }
        // Most devices expose their active GPU under a vendor-specific devfreq node.
        val devfreq = runCatching { File("/sys/class/devfreq").listFiles().orEmpty() }.getOrDefault(emptyArray())
        for (node in devfreq.filter { it.name.contains("gpu", ignoreCase = true) }) {
            val load = File(node, "load")
            val text = runCatching { load.takeIf { it.canRead() }?.readText()?.trim() }.getOrNull()
            val value = text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
            if (value != null && value in 0..100) return "$value%"
        }
        return "n/d"
    }

    private suspend fun readFps(): String {
        if (shell.mode == ExecutionMode.STANDARD || (shell.mode == ExecutionMode.ROOT && !shell.hasRoot())) return "n/d"
        val packageName = prefs.lastForegroundPackage
        val layer = if (packageName.matches(Regex("[A-Za-z0-9_.]+"))) {
            shell.exec("dumpsys SurfaceFlinger --list", record = false).stdout.lineSequence()
                .firstOrNull { it.contains(packageName) }
        } else null
        val command = if (layer.isNullOrBlank()) {
            "dumpsys SurfaceFlinger --latency"
        } else {
            val safeLayer = layer.replace("'", "'\\''")
            "dumpsys SurfaceFlinger --latency '$safeLayer'"
        }
        val output = shell.exec(command, record = false).stdout
        val frameTimes = output.lineSequence().mapNotNull { line ->
            val columns = line.trim().split(Regex("\\s+"))
            columns.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
        }.sorted().toList()
        if (frameTimes.size < 2) return "n/d"
        val last = frameTimes.last()
        val recentCount = frameTimes.count { it >= last - 1_000_000_000L }
        if (recentCount <= 1) return "<1"
        return "$recentCount"
    }
}
