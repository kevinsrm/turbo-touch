package com.kevinsrm.turbotouch.profile

/** Lista de comandos que o perfil vai executar — usado na tela de edicao. */
object ProfilePreview {

    fun commands(profile: AppProfile): List<String> {
        val commands = mutableListOf<String>()
        if (profile.refresh != RefreshTarget.AUTO) {
            commands += "cmd display set-user-preferred-display-mode <w> <h> ${profile.refresh.hz}"
            commands += "settings put system min_refresh_rate ${profile.refresh.hz}"
            commands += "settings put system peak_refresh_rate ${profile.refresh.hz}"
            commands += "settings put secure match_content_frame_rate 0"
        }
        if (profile.gameMode != GameMode.DEFAULT) {
            commands += "cmd game mode ${profile.packageName} ${profile.gameMode.wire}"
        }
        if (profile.lockMaxClocks) {
            commands += "cmd power set-fixed-performance-mode-enabled true"
            commands += "cmd power set-fixed-performance-mode-threshold 95"
        }
        if (profile.aggressiveSaver) {
            commands += "cmd deviceidle force-idle ${profile.packageName}"
            commands += "cmd appops set ${profile.packageName} RUN_ANY_IN_BACKGROUND ignore"
        }
        if (profile.gpuBoost) {
            commands += "echo <max_freq> > /sys/class/devfreq/<gpu>/min_freq"
        }
        if (profile.schedulerBoost) {
            commands += "echo 1 > /dev/cpuctl/top-app/sched_boost"
        }
        return commands
    }

    fun revertCommands(profile: AppProfile): List<String> {
        val commands = mutableListOf<String>()
        if (profile.refresh != RefreshTarget.AUTO) {
            commands += "cmd display clear-user-preferred-display-mode"
            commands += "settings delete system min_refresh_rate"
            commands += "settings delete system peak_refresh_rate"
        }
        if (profile.gameMode != GameMode.DEFAULT) {
            commands += "cmd game mode ${profile.packageName} standard"
        }
        if (profile.lockMaxClocks) {
            commands += "cmd power reset-fixed-performance-mode-enabled"
        }
        if (profile.aggressiveSaver) {
            commands += "cmd deviceidle unforce-idle ${profile.packageName}"
            commands += "cmd appops set ${profile.packageName} RUN_ANY_IN_BACKGROUND default"
        }
        if (profile.gpuBoost) {
            commands += "echo <min_freq salvo> > /sys/class/devfreq/<gpu>/min_freq"
        }
        if (profile.schedulerBoost) {
            commands += "echo 0 > /dev/cpuctl/top-app/sched_boost"
        }
        return commands
    }
}
