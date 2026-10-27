package com.kevinsrm.turbotouch.profile

import com.kevinsrm.turbotouch.core.ShellResult

/**
 * Aplica e reverte perfis de hardware conforme o app em primeiro plano muda.
 *
 * Estado global do aparelho e por app ao mesmo tempo: o motor entra no perfil
 * quando o app entra em primeiro plano e desfaz tudo quando ele sai.
 */
class ProfileEngine(
    private val refresh: RefreshRateController,
    private val power: PowerController,
    private val cpuGpu: CpuGpuController,
    private val store: ProfileStore
) {

    private var appliedPackage: String? = null
    private var appliedProfile: AppProfile? = null

    val activeProfile: AppProfile? get() = appliedProfile
    val activePackage: String? get() = appliedPackage

    suspend fun onForegroundChanged(packageName: String?) {
        if (packageName == appliedPackage) return

        appliedProfile?.let { revert(it) }
        appliedProfile = null
        appliedPackage = packageName

        if (packageName == null) return
        val profile = store.get(packageName) ?: return
        if (!profile.enabled) return

        appliedProfile = profile
        apply(profile)
    }

    suspend fun apply(profile: AppProfile) {
        if (profile.refresh != RefreshTarget.AUTO) {
            refresh.apply(profile.refresh)
        }
        if (profile.gameMode != GameMode.DEFAULT) {
            power.setGameMode(profile.packageName, profile.gameMode)
        }
        if (profile.lockMaxClocks) {
            power.setFixedPerformance(true)
        }
        if (profile.aggressiveSaver) {
            power.setForceIdle(profile.packageName, true)
            power.setBackgroundRestricted(profile.packageName, true)
        }
        if (profile.gpuBoost) {
            cpuGpu.setGpuMaxFreq(true)
        }
        if (profile.schedulerBoost) {
            cpuGpu.setSchedulerBoost(true)
        }
    }

    suspend fun revert(profile: AppProfile) {
        if (profile.refresh != RefreshTarget.AUTO) {
            refresh.restore()
        }
        if (profile.gameMode != GameMode.DEFAULT) {
            power.setGameMode(profile.packageName, GameMode.STANDARD)
        }
        if (profile.lockMaxClocks) {
            power.setFixedPerformance(false)
        }
        if (profile.aggressiveSaver) {
            power.setForceIdle(profile.packageName, false)
            power.setBackgroundRestricted(profile.packageName, false)
        }
        if (profile.gpuBoost) {
            cpuGpu.setGpuMaxFreq(false)
        }
        if (profile.schedulerBoost) {
            cpuGpu.setSchedulerBoost(false)
        }
    }

    suspend fun revertEverything(): List<ShellResult> {
        val results = mutableListOf<ShellResult>()
        appliedProfile?.let { profile ->
            results += revert(profile)
        }
        appliedProfile = null
        appliedPackage = null
        return results
    }
}
