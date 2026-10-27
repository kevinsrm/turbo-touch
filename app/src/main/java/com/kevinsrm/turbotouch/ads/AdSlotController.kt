package com.kevinsrm.turbotouch.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.kevinsrm.turbotouch.data.Prefs

/**
 * Controla os slots de anuncio: o slot de fundo (abre a landing quando o
 * usuario volta para o app) e o banner exibido na tela "Ads".
 */
class AdSlotController(
    private val context: Context,
    private val prefs: Prefs
) {

    private var provider: AdProvider? = null
    private var config: AdConfig = AdConfig()
    private var lastShownAt: Long = 0L
    private var pendingBackground: Boolean = false

    fun isEnabled(): Boolean = prefs.adsEnabledOverride ?: config.enabled

    fun currentConfig(): AdConfig = config

    fun currentProvider(): AdProvider? = provider

    /** Recarrega a configuracao do arquivo + override e (re)cria o provedor. */
    fun reload(): AdConfig {
        config = AdConfig.load(context)
        provider?.release()
        provider = null
        if (isEnabled()) {
            val created = createProvider(config)
            if (created != null && created.initialize(context, config)) {
                provider = created
            } else {
                Log.w(TAG, "provedor ${config.provider} nao inicializou")
            }
        }
        return config
    }

    fun showBanner(container: FrameLayout): Boolean = provider?.loadBanner(container) ?: false

    /** Marca um slot de fundo pendente quando o app vai para segundo plano. */
    fun onBackgrounded() {
        if (!isEnabled()) return
        if (config.backgroundIntervalSec <= 0) return
        val elapsed = System.currentTimeMillis() - lastShownAt
        if (elapsed >= config.backgroundIntervalSec * 1000L) {
            pendingBackground = true
        }
    }

    /** Abre o slot de fundo pendente. true = algo foi exibido. */
    fun onForegrounded(activity: Activity): Boolean {
        if (!pendingBackground) return false
        pendingBackground = false
        val shown = provider?.showInterstitial(activity) ?: false
        if (shown) lastShownAt = System.currentTimeMillis()
        return shown
    }

    /** Usado pelo botao "mostrar agora" da tela de anuncios. */
    fun showNow(activity: Activity): Boolean {
        val shown = provider?.showInterstitial(activity) ?: false
        if (shown) lastShownAt = System.currentTimeMillis()
        return shown
    }

    private fun createProvider(config: AdConfig): AdProvider? = when (config.provider) {
        "smartlink" -> SmartLinkAdProvider()
        "admob" -> AdMobAdProvider()
        "reflective" -> ReflectiveAdProvider()
        else -> null
    }

    private companion object {
        const val TAG = "TurboTouchAds"
    }
}
