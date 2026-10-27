package com.kevinsrm.turbotouch.ads

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout

/**
 * Contrato dos provedores de anuncios.
 *
 * smartlink  -> Adsterra / Monetag / PropellerAds "Direct Link" (sem SDK, sem loja, sem aprovacao)
 * admob      -> Google Mobile Ads SDK (banner + interstitial)
 * reflective -> qualquer outro SDK, ligado por nome de classe via reflexao
 */
interface AdProvider {

    val name: String

    fun initialize(context: Context, config: AdConfig): Boolean

    fun loadBanner(container: FrameLayout): Boolean

    fun showInterstitial(activity: Activity): Boolean

    fun isReady(): Boolean

    fun release()
}
