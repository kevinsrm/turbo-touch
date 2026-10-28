package com.kevinsrm.turbotouch.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Banner + interstitial do Google Mobile Ads.
 *
 * O app id do AdMob vem do manifest (manifestPlaceholders, lido do
 * ads_config.properties no build). Os ids de bloco podem ser trocados em
 * tempo de execucao pela tela "Ads".
 */
class AdMobAdProvider : AdProvider {

    override val name: String = "admob"

    private var bannerUnit: String = ""
    private var interstitialUnit: String = ""
    private var interstitial: InterstitialAd? = null
    private var initialized = false

    /** dispara quando o SDK reporta o clique do anuncio. */
    var onAdClick: (() -> Unit)? = null

    override fun initialize(context: Context, config: AdConfig): Boolean {
        bannerUnit = config.admobBannerUnit
        interstitialUnit = config.admobInterstitialUnit
        return try {
            if (config.debug) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(EMULATOR_DEVICE_ID))
                        .build()
                )
            }
            MobileAds.initialize(context) { }
            initialized = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "AdMob init falhou: ${t.message}")
            false
        }
    }

    override fun loadBanner(container: FrameLayout): Boolean {
        if (!initialized || bannerUnit.isBlank()) return false
        val adView = AdView(container.context)
        adView.adUnitId = bannerUnit
        adView.setAdSize(AdSize.BANNER)
        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                onAdClick?.invoke()
            }
        }
        container.removeAllViews()
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
        return true
    }

    override fun showInterstitial(activity: Activity): Boolean {
        val ready = interstitial
        if (ready != null) {
            ready.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    onAdClick?.invoke()
                }

                override fun onAdDismissedFullScreenContent() {
                    interstitial = null
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitial = null
                }
            }
            interstitial = null
            ready.show(activity)
            return true
        }

        if (!initialized || interstitialUnit.isBlank()) return false
        InterstitialAd.load(
            activity,
            interstitialUnit,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitial = null
                }
            }
        )
        return false
    }

    override fun isReady(): Boolean = initialized

    override fun release() {
        interstitial = null
        initialized = false
    }

    private companion object {
        const val TAG = "TurboTouchAds"
        const val EMULATOR_DEVICE_ID = "33BE2250B43518FCDA0DE1E6E8B59D35"
    }
}
