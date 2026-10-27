package com.kevinsrm.turbotouch.ads

import android.content.Context
import java.io.File
import java.util.Properties

/**
 * Configuracao do modulo de anuncios.
 *
 * Origem, em ordem de precedencia:
 *   1. app/src/main/assets/ads_config.properties  (gerado no build a partir de
 *      ads_config.properties na raiz do repositorio)
 *   2. override em tempo de execucao (tela "Ads"), salvo em
 *      filesDir/ads_config_override.properties
 */
data class AdConfig(
    val enabled: Boolean = false,
    val provider: String = "none",
    val smartLinkUrl: String = "",
    val admobAppId: String = "",
    val admobBannerUnit: String = "",
    val admobInterstitialUnit: String = "",
    val backgroundIntervalSec: Int = 45,
    val debug: Boolean = false,
    val reflectiveInitClass: String = "",
    val reflectiveInitMethod: String = "initialize",
    val reflectiveBannerClass: String = "",
    val reflectiveInterstitialClass: String = ""
) {

    val isSmartLink: Boolean get() = provider == "smartlink"
    val isAdMob: Boolean get() = provider == "admob"
    val isReflective: Boolean get() = provider == "reflective"

    fun asProperties(): Properties {
        val props = Properties()
        props.setProperty("ads.enabled", enabled.toString())
        props.setProperty("ads.provider", provider)
        props.setProperty("ads.smartlink.url", smartLinkUrl)
        props.setProperty("ads.admob.app_id", admobAppId)
        props.setProperty("ads.admob.banner_unit", admobBannerUnit)
        props.setProperty("ads.admob.interstitial_unit", admobInterstitialUnit)
        props.setProperty("ads.background.interval_sec", backgroundIntervalSec.toString())
        props.setProperty("ads.debug", debug.toString())
        props.setProperty("ads.reflective.init_class", reflectiveInitClass)
        props.setProperty("ads.reflective.init_method", reflectiveInitMethod)
        props.setProperty("ads.reflective.banner_class", reflectiveBannerClass)
        props.setProperty("ads.reflective.interstitial_class", reflectiveInterstitialClass)
        return props
    }

    companion object {
        const val ASSET_NAME = "ads_config.properties"
        private const val OVERRIDE_FILE = "ads_config_override.properties"

        fun fromProperties(props: Properties): AdConfig = AdConfig(
            enabled = props.getProperty("ads.enabled", "false").trim().toBoolean(),
            provider = props.getProperty("ads.provider", "none").trim().lowercase(),
            smartLinkUrl = props.getProperty("ads.smartlink.url", "").trim(),
            admobAppId = props.getProperty("ads.admob.app_id", "").trim(),
            admobBannerUnit = props.getProperty("ads.admob.banner_unit", "").trim(),
            admobInterstitialUnit = props.getProperty("ads.admob.interstitial_unit", "").trim(),
            backgroundIntervalSec = props.getProperty("ads.background.interval_sec", "45").trim().toIntOrNull() ?: 45,
            debug = props.getProperty("ads.debug", "false").trim().toBoolean(),
            reflectiveInitClass = props.getProperty("ads.reflective.init_class", "").trim(),
            reflectiveInitMethod = props.getProperty("ads.reflective.init_method", "initialize").trim(),
            reflectiveBannerClass = props.getProperty("ads.reflective.banner_class", "").trim(),
            reflectiveInterstitialClass = props.getProperty("ads.reflective.interstitial_class", "").trim()
        )

        fun load(context: Context): AdConfig {
            val props = Properties()
            try {
                context.assets.open(ASSET_NAME).use { props.load(it) }
            } catch (t: Throwable) {
                // build sem ads_config.properties: segue com os defaults
            }
            overrideProperties(context)?.let { props.putAll(it) }
            return fromProperties(props)
        }

        fun overrideProperties(context: Context): Properties? {
            val file = File(context.filesDir, OVERRIDE_FILE)
            if (!file.exists()) return null
            val props = Properties()
            try {
                file.inputStream().use { props.load(it) }
            } catch (t: Throwable) {
                return null
            }
            return props
        }

        fun saveOverride(context: Context, props: Properties) {
            val text = props.stringPropertyNames().sorted()
                .joinToString("\n") { "$it=${props.getProperty(it)}" }
            File(context.filesDir, OVERRIDE_FILE).writeText(text)
        }
    }
}
