package com.kevinsrm.turbotouch.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import java.lang.reflect.Method

/**
 * Liga qualquer SDK de anuncios por nome de classe, sem dependencia de
 * compilacao. Use quando voce adiciona o AAR/dependencia do seu SDK no
 * build.gradle.kts e so falta o glue.
 *
 * O binder procura metodos com os nomes usuais (load / loadAd / show / setZone)
 * e registra tudo no log, entao da para ajustar os nomes em
 * ads_config.properties.
 */
class ReflectiveAdProvider : AdProvider {

    override val name: String = "reflective"

    private var initClass: Class<*>? = null
    private var initMethodName: String = "initialize"
    private var bannerClass: Class<*>? = null
    private var interstitialClass: Class<*>? = null
    private var bannerInstance: Any? = null
    private var interstitialInstance: Any? = null
    private var initialized = false
    private var appContext: Context? = null

    override fun initialize(context: Context, config: AdConfig): Boolean {
        appContext = context
        initMethodName = config.reflectiveInitMethod.ifBlank { "initialize" }
        initClass = loadClass(config.reflectiveInitClass)
        bannerClass = loadClass(config.reflectiveBannerClass)
        interstitialClass = loadClass(config.reflectiveInterstitialClass)

        val target = initClass ?: return false
        val method = target.methods.firstOrNull { it.name == initMethodName }
            ?: target.methods.firstOrNull { it.name.startsWith(initMethodName) }
        if (method == null) {
            Log.w(TAG, "metodo $initMethodName nao encontrado em ${target.name}")
            return false
        }
        val args = buildArgs(method, context)
        return try {
            method.invoke(null, *args)
            initialized = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "init por reflexao falhou: ${t.message}")
            false
        }
    }

    override fun loadBanner(container: FrameLayout): Boolean {
        val type = bannerClass ?: return false
        val instance = instantiate(type, container.context) ?: return false
        bannerInstance = instance
        val view = instance as? android.view.View ?: return false
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        invokeFirst(instance, listOf("load", "loadAd", "show", "setZone"))
        return true
    }

    override fun showInterstitial(activity: Activity): Boolean {
        val type = interstitialClass ?: return false
        var instance = interstitialInstance ?: instantiate(type, activity).also { interstitialInstance = it }
        if (instance == null) return false
        invokeFirst(instance, listOf("load", "loadAd"))
        val shown = invokeFirst(instance, listOf("show", "showAd", "present"))
        if (shown) interstitialInstance = null
        return shown
    }

    override fun isReady(): Boolean = initialized

    override fun release() {
        bannerInstance = null
        interstitialInstance = null
        initialized = false
    }

    private fun loadClass(name: String): Class<*>? {
        if (name.isBlank()) return null
        return try {
            Class.forName(name)
        } catch (t: Throwable) {
            Log.w(TAG, "classe $name nao encontrada no classpath")
            null
        }
    }

    private fun instantiate(type: Class<*>, context: Context): Any? {
        return try {
            type.getDeclaredConstructor(Context::class.java).newInstance(context)
        } catch (t: Throwable) {
            try {
                type.getDeclaredConstructor().newInstance()
            } catch (t2: Throwable) {
                Log.w(TAG, "nao foi possivel instanciar ${type.name}: ${t2.message}")
                null
            }
        }
    }

    private fun buildArgs(method: Method, context: Context?): Array<Any?> {
        val params = method.parameterTypes
        val args = arrayOfNulls<Any?>(params.size)
        for (i in params.indices) {
            args[i] = when {
                params[i].isAssignableFrom(Context::class.java) -> context
                params[i] == String::class.java -> ""
                params[i] == Boolean::class.java -> true
                params[i] == Int::class.java -> 0
                else -> null
            }
        }
        return args
    }

    private fun invokeFirst(instance: Any, names: List<String>): Boolean {
        for (name in names) {
            val method = instance.javaClass.methods.firstOrNull { it.name == name } ?: continue
            val args = buildArgs(method, appContext)
            try {
                method.invoke(instance, *args)
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "${instance.javaClass.simpleName}.$name falhou: ${t.message}")
            }
        }
        return false
    }

    private companion object {
        const val TAG = "TurboTouchAds"
    }
}
