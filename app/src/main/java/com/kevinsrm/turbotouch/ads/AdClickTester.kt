package com.kevinsrm.turbotouch.ads

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Harness de QA para os proprios placements de anuncio do app.
 *
 * Dispara um toque sintetico no centro de uma view de anuncio e devolve true
 * quando o evento foi entregue. Serve para conferir se o clique chega no SDK e
 * se o destino abre — com os ids de teste do proprio anunciante.
 */
object AdClickTester {

    fun syntheticTap(view: View): Boolean {
        if (!view.isShown || view.width <= 0 || view.height <= 0) return false
        val x = view.width / 2f
        val y = view.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        val delivered = try {
            val first = view.dispatchTouchEvent(down)
            val second = view.dispatchTouchEvent(up)
            first && second
        } catch (t: Throwable) {
            false
        } finally {
            down.recycle()
            up.recycle()
        }
        return delivered
    }
}
