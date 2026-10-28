package com.kevinsrm.turbotouch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsParsersTest {

    @Test
    fun leBlocoCompletoDoScript() {
        val output = """
            @@@STAT
            cpu  100 0 100 800 0 0 0 0 0 0
            @@@GPU
            @@@GPUFILE:/sys/class/kgsl/kgsl-3d0/gpubusy
             200 1000
            @@@FPS
            @@@LAT:SurfaceView[com.example/.Main](BLAST)#0
            16666666
            0	0	0
            1000	1000	1000
            @@@GFX
            Total frames rendered: 4242
            @@@END
        """.trimIndent()

        val sections = MetricsParsers.parseSections(output)

        assertEquals("cpu  100 0 100 800 0 0 0 0 0 0", sections.stat)
        assertEquals(1, sections.gpu.size)
        assertEquals("/sys/class/kgsl/kgsl-3d0/gpubusy", sections.gpu.first().path)
        assertEquals("200 1000", sections.gpu.first().value)
        assertEquals(1, sections.latencies.size)
        assertTrue(sections.latencies.containsKey("SurfaceView[com.example/.Main](BLAST)#0"))
        assertEquals(4242L, sections.totalFrames)
    }

    @Test
    fun cpuUsaDeltaEntreAmostras() {
        val first = MetricsParsers.ticks("cpu  10 0 10 80 0 0 0 0 0 0")!!
        val second = MetricsParsers.ticks("cpu  30 0 30 140 0 0 0 0 0 0")!!

        assertNull(MetricsParsers.cpuPercent(null, first))
        // 40 ticks de trabalho em 100 ticks totais.
        assertEquals(40, MetricsParsers.cpuPercent(first, second))
    }

    @Test
    fun cpuIgnoraLinhasQueNaoSaoDoAgregado() {
        assertNull(MetricsParsers.ticks("cpu0 1 2 3 4"))
        assertNull(MetricsParsers.ticks("intr 123"))
        assertNull(MetricsParsers.ticks(null))
    }

    @Test
    fun gpuAceitaPercentualERatio() {
        assertEquals(12, MetricsParsers.gpuPercent("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "12 %"))
        assertEquals(20, MetricsParsers.gpuPercent("/sys/class/kgsl/kgsl-3d0/gpubusy", "200 1000"))
        assertEquals(35, MetricsParsers.gpuPercent("/sys/kernel/ged/hal/gpu_utilization", "35 400000000 800000000"))
        assertEquals(48, MetricsParsers.gpuPercent("/sys/class/devfreq/5000000.mali/load", "48"))
    }

    @Test
    fun gpuDescartaValoresImplausiveis() {
        assertNull(MetricsParsers.gpuPercent("/sys/kernel/gpu/gpu_busy", "800000000"))
        assertNull(MetricsParsers.gpuPercent("/sys/class/kgsl/kgsl-3d0/gpubusy", "0 0"))
        assertNull(MetricsParsers.gpuPercent("/caminho/desconhecido", "7"))
    }

    @Test
    fun fpsContaApresentacoesDoUltimoSegundo() {
        val now = 1_000_000_000_000L
        val lines = buildString {
            append("16666666\n")
            repeat(60) { index ->
                val timestamp = now - index * 16_666_666L
                append("0\t").append(timestamp).append('\t').append(timestamp).append('\n')
            }
        }
        assertEquals(60.0, MetricsParsers.latencyFps(lines, now)!!, 0.01)
        assertEquals("60", MetricsParsers.formatFps(MetricsParsers.latencyFps(lines, now)!!))
    }

    @Test
    fun fpsZeraQuandoACamadaParou() {
        val now = 1_000_000_000_000L
        val lines = buildString {
            append("16666666\n")
            repeat(10) { index ->
                val timestamp = now - 5_000_000_000L - index * 16_666_666L
                append("0\t").append(timestamp).append('\t').append(timestamp).append('\n')
            }
        }
        assertEquals(0.0, MetricsParsers.latencyFps(lines, now)!!, 0.01)
    }

    @Test
    fun fpsIgnoraFramesPendentes() {
        val now = 1_000_000_000_000L
        val lines = """
            16666666
            0	0	0
            0	${now - 1_000_000_000L}	0
            0	${now - 500_000_000L}	0
            ${now}	${Long.MAX_VALUE}	0
        """.trimIndent()
        // Sobram duas apresentacoes reais, separadas por 500 ms.
        assertEquals(2.0, MetricsParsers.latencyFps(lines, now)!!, 0.01)
    }

    @Test
    fun prefereCamadaDoAppSobreCamadasDeSistema() {
        val app = "SurfaceView[com.example/com.example.Main](BLAST)#0"
        assertTrue(MetricsParsers.scoreLayer(app) > MetricsParsers.scoreLayer("com.example/com.example.Main#1"))
        assertTrue(MetricsParsers.scoreLayer(app) > 0)
        assertTrue(MetricsParsers.scoreLayer("StatusBar#0") < 0)
        assertTrue(MetricsParsers.scoreLayer("Background for com.example") < 0)
    }

    @Test
    fun escapaAspasSimplesParaOShell() {
        assertEquals("'abc'", MetricsParsers.singleQuote("abc"))
        assertEquals("'a'\\''b'", MetricsParsers.singleQuote("a'b"))
        assertTrue(MetricsParsers.isLayerNameSafe("SurfaceView[com.example/.Main]#0"))
        assertTrue(!MetricsParsers.isLayerNameSafe("camada\nquebrada"))
    }
}
