package com.kevinsrm.turbotouch.service

import kotlin.math.roundToInt

/**
 * Parsers puros (sem nenhuma API do Android) usados pelo HUD.
 *
 * Ficam separados do [MonitorMetrics] para poderem ser testados na JVM: o app pode
 * rodar como root, como uid `shell` (ADB Wi-Fi) ou sem privilegio, e o texto que
 * chega de cada caminho tem formato diferente.
 */
internal object MetricsParsers {

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER = Regex("\\d+")
    private val FRAMES_RENDERED = Regex("Total frames rendered:\\s*(\\d+)")

    const val MARK_STAT = "@@@STAT"
    const val MARK_GPU = "@@@GPU"
    const val MARK_GPU_FILE = "@@@GPUFILE:"
    const val MARK_FPS = "@@@FPS"
    const val MARK_LATENCY = "@@@LAT:"
    const val MARK_GFX = "@@@GFX"
    const val MARK_END = "@@@END"

    /**
     * Nodulos sysfs onde fabricantes publicam uso de GPU. A ordem e a ordem de
     * preferencia: os primeiros entregam percentual direto, os ultimos sao
     * aproximacoes (devfreq `load`).
     */
    val GPU_CANDIDATES = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/kernel/ged/hal/gpu_utilization",
        "/sys/kernel/gpu/gpu_loading",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/mali0/device/utilisation",
        "/sys/devices/platform/*.mali/utilisation",
        "/sys/devices/platform/*.gpu/utilisation",
        "/sys/devices/platform/soc/*.gpu/utilisation",
        "/sys/devices/platform/*.gpu/devfreq/*/load",
        "/sys/class/devfreq/*gpu*/load",
        "/sys/class/devfreq/*mali*/load",
        "/sys/class/devfreq/*kgsl*/load"
    )

    /** Camadas do SurfaceFlinger que nunca representam o app em primeiro plano. */
    private val IGNORED_LAYERS = listOf(
        "background", "dim layer", "splash", "statusbar", "navigationbar",
        "notification", "wallpaper", "inputmethod", "toast", "bubble",
        "snapshot", "pip", "screenrecord", "pointer"
    )

    data class Ticks(val total: Long, val idle: Long)

    data class GpuReading(val path: String, val value: String)

    data class Sections(
        val stat: String? = null,
        val gpu: List<GpuReading> = emptyList(),
        val latencies: Map<String, String> = emptyMap(),
        val totalFrames: Long? = null,
        val raw: String = ""
    )

    /** Le o bloco `@@@...` produzido pelo script de coleta. */
    fun parseSections(output: String): Sections {
        var stat: String? = null
        val gpu = mutableListOf<GpuReading>()
        val latencies = LinkedHashMap<String, StringBuilder>()
        var totalFrames: Long? = null

        var mode = Mode.NONE
        var gpuPath: String? = null
        val gpuValue = StringBuilder()
        var layer: String? = null

        fun flushGpu() {
            val path = gpuPath
            if (path != null && gpuValue.isNotBlank()) {
                gpu += GpuReading(path, gpuValue.toString().trim())
            }
            gpuPath = null
            gpuValue.setLength(0)
        }

        fun flushLayer() {
            layer = null
        }

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            when {
                line == MARK_STAT -> {
                    flushGpu(); flushLayer(); mode = Mode.STAT
                }
                line == MARK_GPU -> {
                    flushGpu(); flushLayer(); mode = Mode.GPU
                }
                line == MARK_FPS -> {
                    flushGpu(); flushLayer(); mode = Mode.FPS
                }
                line == MARK_GFX -> {
                    flushGpu(); flushLayer(); mode = Mode.GFX
                }
                line == MARK_END -> {
                    flushGpu(); flushLayer(); mode = Mode.NONE
                }
                line.startsWith(MARK_GPU_FILE) -> {
                    flushGpu(); flushLayer()
                    gpuPath = line.removePrefix(MARK_GPU_FILE)
                    mode = Mode.GPU
                }
                line.startsWith(MARK_LATENCY) -> {
                    flushGpu()
                    val name = line.removePrefix(MARK_LATENCY)
                    layer = name
                    latencies.getOrPut(name) { StringBuilder() }
                    mode = Mode.LATENCY
                }
                line.isBlank() -> Unit
                mode == Mode.STAT -> if (stat == null) stat = line.trim()
                mode == Mode.GPU -> gpuValue.append(line).append(' ')
                mode == Mode.LATENCY -> layer?.let { latencies[it]?.append(line)?.append('\n') }
                mode == Mode.GFX -> {
                    val frames = FRAMES_RENDERED.find(line)?.groupValues?.get(1)?.toLongOrNull()
                    if (frames != null) totalFrames = frames
                }
            }
        }
        flushGpu()
        return Sections(
            stat = stat,
            gpu = gpu.toList(),
            latencies = latencies.mapValues { it.value.toString() },
            totalFrames = totalFrames,
            raw = output
        )
    }

    /** Primeira linha de `/proc/stat` -> ticks agregados da CPU. */
    fun ticks(line: String?): Ticks? {
        if (line.isNullOrBlank()) return null
        val values = line.trim().split(WHITESPACE)
        if (values.firstOrNull() != "cpu" || values.size < 5) return null
        val numbers = values.drop(1).mapNotNull { it.toLongOrNull() }
        if (numbers.size < 4) return null
        val total = numbers.sum()
        if (total <= 0L) return null
        val idle = numbers[3] + (numbers.getOrNull(4) ?: 0L)
        return Ticks(total, idle)
    }

    /** Percentual de CPU ocupada entre duas amostras; null quando ainda nao ha base. */
    fun cpuPercent(previous: Ticks?, current: Ticks): Int? {
        if (previous == null) return null
        val totalDelta = current.total - previous.total
        if (totalDelta <= 0L) return null
        val idleDelta = (current.idle - previous.idle).coerceIn(0L, totalDelta)
        return ((totalDelta - idleDelta) * 100.0 / totalDelta).roundToInt().coerceIn(0, 100)
    }

    /** Melhor leitura de GPU disponivel, na ordem em que o script as coletou. */
    fun gpuPercent(readings: List<GpuReading>): Int? {
        for (reading in readings) {
            val percent = gpuPercent(reading.path, reading.value) ?: continue
            return percent
        }
        return null
    }

    fun gpuPercent(path: String, value: String): Int? {
        val numbers = NUMBER.findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
        if (numbers.isEmpty()) return null
        val lower = path.lowercase()
        val percent: Int? = when {
            lower.contains("gpubusy") -> {
                val total = numbers.getOrNull(1) ?: return null
                if (total <= 0L) {
                    null
                } else {
                    val busy = minOf(numbers[0], total)
                    ((busy.toDouble() * 100.0) / total).roundToInt()
                }
            }
            lower.contains("gpu_utilization") || lower.contains("gpu_loading") -> numbers[0].toInt()
            lower.endsWith("/load") -> numbers[0].toInt()
            lower.contains("percent") || lower.contains("utilisation") ||
                lower.contains("utilization") || lower.contains("busy") -> numbers[0].toInt()
            else -> null
        }
        return percent?.takeIf { it in 0..100 }
    }

    /**
     * `dumpsys SurfaceFlinger --latency <camada>` devolve, por linha, os instantes
     * (CLOCK_MONOTONIC em ns) de vsync esperado, apresentado e pronto. Contamos as
     * apresentacoes do ultimo segundo; camada parada devolve 0.
     */
    fun latencyFps(text: String, nowNanos: Long): Double? {
        if (text.isBlank()) return null
        val frames = mutableListOf<Long>()
        for (rawLine in text.lineSequence()) {
            val columns = rawLine.trim().split(WHITESPACE)
            if (columns.size < 3) continue
            val actual = columns[1].toLongOrNull() ?: continue
            if (actual <= 0L || actual > nowNanos + 1_000_000_000L) continue
            frames += actual
        }
        if (frames.size < 2) return null
        val last = frames.maxOrNull() ?: return null
        if (nowNanos - last > 1_500_000_000L) return 0.0
        val window = frames.filter { it >= last - 1_000_000_000L }.sorted()
        if (window.size < 2) return 1.0
        val span = window.last() - window.first()
        if (span <= 0L) return null
        return (window.size - 1) * 1_000_000_000.0 / span
    }

    fun formatFps(fps: Double): String = fps.roundToInt().coerceIn(0, 999).toString()

    /** Nome de camada que pode ir para o shell sem escapar nada exotico. */
    fun isLayerNameSafe(layer: String): Boolean =
        layer.isNotEmpty() && layer.length <= 200 && layer.all { it.code in 0x20..0x7E }

    /** Prioriza a camada que desenha o app (SurfaceView/BLAST#0) e descarta as de sistema. */
    fun scoreLayer(layer: String): Int {
        val lower = layer.lowercase()
        if (IGNORED_LAYERS.any { lower.contains(it) }) return -1
        var score = 0
        if (lower.contains("surfaceview")) score += 3
        if (lower.contains("blast")) score += 1
        if (lower.contains("#0")) score += 2
        if (lower.contains("activity")) score += 1
        return score
    }

    /** Escapa um valor para uso entre aspas simples no shell. */
    fun singleQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private enum class Mode { NONE, STAT, GPU, FPS, LATENCY, GFX }
}
