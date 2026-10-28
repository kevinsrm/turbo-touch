package com.kevinsrm.turbotouch.ads

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock

/** Oferta identificada como patrocinada, aberta no navegador do usuario apos salvar um perfil. */
class AdSlotController {
    private var lastShownAt: Long? = null

    fun showAfterAction(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastShownAt?.let { now - it < INTERVAL_MS } == true) return
        AlertDialog.Builder(context)
            .setTitle("Anúncio patrocinado")
            .setMessage("Veja uma oferta no navegador.")
            .setPositiveButton("Abrir") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SMART_LINK))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching { context.startActivity(intent) }
            }
            .setNegativeButton("Agora não", null)
            .show()
        lastShownAt = now
    }

    private companion object {
        const val SMART_LINK = "https://auctionr.org/4/1f49efe58b541fee5c0b90a25d9a7c86"
        const val INTERVAL_MS = 120_000L
    }
}
