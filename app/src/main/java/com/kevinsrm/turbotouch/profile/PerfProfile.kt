package com.kevinsrm.turbotouch.profile

/** Modo de game do Android 12+ (GameManagerService). */
enum class GameMode(val wire: String, val numeric: Int, val label: String) {
    DEFAULT("default", 1, "Padrão do sistema"),
    STANDARD("standard", 1, "Standard"),
    PERFORMANCE("performance", 2, "Performance"),
    BATTERY("battery", 3, "Battery");

    companion object {
        fun fromWire(value: String?): GameMode =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}

/** Alvo de taxa de atualizacao forcado por app. */
enum class RefreshTarget(val hz: Int, val label: String) {
    AUTO(0, "Automático"),
    HZ60(60, "60 Hz"),
    HZ90(90, "90 Hz"),
    HZ120(120, "120 Hz"),
    HZ144(144, "144 Hz");

    companion object {
        fun fromHz(hz: Int): RefreshTarget =
            entries.firstOrNull { it.hz == hz } ?: AUTO
    }
}

/**
 * Perfil de hardware por aplicativo.
 *
 * refresh          -> trava o painel no refresh alvo enquanto o app esta em primeiro plano
 * gameMode         -> cmd game mode <pkg> performance|battery|standard
 * lockMaxClocks    -> cmd power set-fixed-performance-mode-enabled true
 * aggressiveSaver  -> cmd deviceidle force-idle + appops RUN_ANY_IN_BACKGROUND ignore
 * gpuBoost         -> devfreq da GPU preso no max_freq
 * schedulerBoost   -> /dev/cpuctl/top-app/sched_boost = 1
 */
data class AppProfile(
    val packageName: String,
    val appLabel: String = "",
    val refresh: RefreshTarget = RefreshTarget.AUTO,
    val gameMode: GameMode = GameMode.DEFAULT,
    val lockMaxClocks: Boolean = false,
    val aggressiveSaver: Boolean = false,
    val gpuBoost: Boolean = false,
    val schedulerBoost: Boolean = false,
    val enabled: Boolean = true
)
