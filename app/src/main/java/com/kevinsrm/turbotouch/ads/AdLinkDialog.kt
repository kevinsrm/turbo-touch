package com.kevinsrm.turbotouch.ads

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Abre uma landing page de anuncio dentro do proprio app.
 *
 * Serve tanto para o slot de fundo (popunder no retorno) quanto para o banner
 * em forma de barra: o usuario ve a pagina e decide.
 */
object AdLinkDialog {

    fun open(context: Context, url: String, title: String = "Anuncio"): Boolean {
        if (url.isBlank()) return false
        return try {
            val dialog = Dialog(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 16, 16, 16)
                setBackgroundColor(Color.parseColor("#101418"))
            }
            val label = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            val close = Button(context).apply {
                text = "Fechar"
                setOnClickListener { dialog.dismiss() }
            }
            header.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(close)

            val web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.webViewClient = WebViewClient()
            web.loadUrl(url)

            root.addView(header)
            root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            dialog.setContentView(root)
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener {
                web.stopLoading()
                web.destroy()
            }
            dialog.show()
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            true
        } catch (t: Throwable) {
            false
        }
    }
}
