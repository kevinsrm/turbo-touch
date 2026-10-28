package com.kevinsrm.turbotouch.ads

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Provedor "Direct Link" / Smartlink.
 *
 * Nao usa SDK nenhum: o anuncio e uma URL de zona (Adsterra, Monetag,
 * PropellerAds). Essas redes sao self-serve, nao exigem o app publicado em loja
 * de aplicativos e nao passam por aprovacao de SDK.
 */
class SmartLinkAdProvider : AdProvider {

    override val name: String = "smartlink"

    private var url: String = ""

    override fun initialize(context: Context, config: AdConfig): Boolean {
        url = config.smartLinkUrl
        return url.isNotBlank()
    }

    override fun loadBanner(container: FrameLayout): Boolean {
        if (url.isBlank()) return false
        val context = container.context
        container.removeAllViews()
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#0F1720"))
            isClickable = true
            setOnClickListener { AdLinkDialog.open(context, url, "Oferta") }
        }
        val label = TextView(context).apply {
            text = "Patrocinado — ver oferta"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        bar.addView(label)
        container.addView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return true
    }

    override fun showInterstitial(activity: Activity): Boolean =
        AdLinkDialog.open(activity, url, "Oferta")

    override fun isReady(): Boolean = url.isNotBlank()

    override fun release() {
        url = ""
    }
}
