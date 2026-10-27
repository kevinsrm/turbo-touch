package com.kevinsrm.turbotouch.profile

import com.kevinsrm.turbotouch.core.RootShell
import com.kevinsrm.turbotouch.core.ShellResult

/**
 * Boost de scheduler (top-app) e trava de frequencia da GPU via devfreq.
 */
class CpuGpuController(private val shell: RootShell) {

    private var savedGpuPath: String? = null
    private var savedGpuMinFreq: Long? = null

    private val schedBoostPaths = listOf(
        "/dev/cpuctl/top-app/sched_boost",
        "/dev/stune/boost"
    )

    suspend fun setSchedulerBoost(enabled: Boolean): ShellResult {
        val value = if (enabled) 1 else 0
        for (path in schedBoostPaths) {
            val exists = shell.exec("ls $path")
            if (exists.ok) {
                return shell.exec("echo $value > $path")
            }
        }
        return ShellResult(1, "", "nenhum arquivo sched_boost encontrado neste aparelho")
    }

    suspend fun setGpuMaxFreq(enabled: Boolean): ShellResult {
        if (!enabled) {
            val path = savedGpuPath
            val freq = savedGpuMinFreq
            savedGpuPath = null
            savedGpuMinFreq = null
            if (path == null || freq == null) {
                return ShellResult(0, "gpu ja estava no estado padrao", "")
            }
            return shell.exec("echo $freq > $path/min_freq")
        }

        val listing = shell.exec("ls /sys/class/devfreq")
        val dir = listing.stdout
            .split(Regex("\\s+"))
            .firstOrNull { it.contains("gpu", ignoreCase = true) }
            ?: return ShellResult(1, "", "nenhum devfreq de gpu em /sys/class/devfreq")

        val base = "/sys/class/devfreq/$dir"
        val maxFreq = shell.exec("cat $base/max_freq").stdout.trim().toLongOrNull()
            ?: return ShellResult(1, "", "max_freq ilegivel em $base")
        val minFreq = shell.exec("cat $base/min_freq").stdout.trim().toLongOrNull()

        savedGpuPath = base
        savedGpuMinFreq = minFreq

        return shell.exec("echo $maxFreq > $base/min_freq")
    }
}
