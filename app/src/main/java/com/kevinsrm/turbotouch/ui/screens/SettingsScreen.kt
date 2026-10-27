package com.kevinsrm.turbotouch.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kevinsrm.turbotouch.ui.Divider
import com.kevinsrm.turbotouch.ui.InfoRow
import com.kevinsrm.turbotouch.ui.MonoText
import com.kevinsrm.turbotouch.ui.SectionCard
import com.kevinsrm.turbotouch.ui.TurboUiState
import com.kevinsrm.turbotouch.ui.TurboViewModel

@Composable
fun SettingsScreen(state: TurboUiState, viewModel: TurboViewModel) {
    val context = LocalContext.current

    fun openIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            viewModel.showMessage("nao foi possivel abrir as configuracoes do sistema")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Permissoes") {
            InfoRow(
                "Root",
                if (state.rootAvailable) "disponivel" else "sem root: modo reduzido (refresh global via WRITE_SETTINGS)"
            )
            InfoRow(
                "Acesso a dados de uso",
                if (state.usageAccessGranted) "concedido" else "necessario para detectar o app em primeiro plano sem root"
            )
            InfoRow(
                "Escrever settings",
                if (state.canWriteSettings) "concedido" else "necessario para ajustar refresh sem root"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    openIntent(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text("Acesso de uso")
                }
                OutlinedButton(onClick = {
                    openIntent(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) {
                    Text("Ajustar settings")
                }
            }
            OutlinedButton(onClick = {
                openIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) {
                Text("Otimizacao de bateria")
            }
        }

        SectionCard("Historico de comandos") {
            if (state.log.isEmpty()) {
                Text("nenhum comando executado ainda", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.log.take(40).forEach { entry ->
                    MonoText("${entry.time} [${entry.exitCode}] ${entry.command}")
                }
            }
            OutlinedButton(onClick = { viewModel.clearLog() }) {
                Text("Limpar historico")
            }
        }

        SectionCard("Sobre") {
            InfoRow("App", "TurboTouch")
            InfoRow("Pacote", context.packageName)
            InfoRow("Versao", "1.0.0")
            Divider()
            Text(
                "Perfil de hardware por aplicativo: refresh do painel, modo de game, " +
                    "lock de clocks, economia agressiva e boost de GPU/GPU.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
