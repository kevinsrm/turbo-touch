package com.kevinsrm.turbotouch.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.kevinsrm.turbotouch.data.Prefs

/** Deve ser usado apenas na thread principal, enquanto o servico esta ativo. */
class MonitorOverlay(private val context: Context, private val prefs: Prefs) {
    private val window = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    fun update(metrics: MonitorMetrics.Snapshot) {
        if ((!prefs.hudFps && !prefs.hudRefresh && !prefs.hudBattery && !prefs.hudCpuGpu) || !Settings.canDrawOverlays(context)) {
            close()
            return
        }
        val lines = mutableListOf<String>()
        if (prefs.hudFps) lines += "FPS: ${metrics.fps}"
        if (prefs.hudCpuGpu) lines += "CPU: ${metrics.cpu} · GPU: ${metrics.gpu}"
        if (prefs.hudRefresh) {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            lines += display?.let { "Tela: %.1f Hz".format(it.refreshRate) } ?: "Tela: indisponível"
        }
        if (prefs.hudBattery) {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val current = context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val percent = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "indisponível"
            val temperature = if (temp >= 0) "%.1f °C".format(temp / 10f) else "indisponível"
            val amperage = if (current != Int.MIN_VALUE) "%.0f mA".format(current / 1000f) else "indisponível"
            lines += "Bateria: $percent · $temperature\nCorrente: $amperage"
        }
        if (lines.isEmpty()) {
            close()
            return
        }
        ensureView()
        view?.text = buildText(lines.joinToString("\n"), metrics.hint)
    }

    /** Corpo do HUD normal e a dica em corpo menor/cinza, sem quebrar a largura da tela. */
    private fun buildText(body: String, hint: String): CharSequence {
        if (hint.isBlank()) return body
        val text = SpannableStringBuilder(body)
        val start = text.length
        text.append('\n').append(hint)
        text.setSpan(RelativeSizeSpan(0.78f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(0xFFA8B7C4.toInt()), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun ensureView() {
        if (view != null) return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val label = TextView(context).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC101418.toInt())
            setPadding(16, 12, 16, 12)
            textSize = 14f
            maxWidth = (screenWidth * 0.72f).toInt()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; y = 100 }
        if (runCatching { window.addView(label, params) }.isSuccess) view = label
    }

    fun close() {
        view?.let { runCatching { window.removeView(it) } }
        view = null
    }
}
