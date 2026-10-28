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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kevinsrm.turbotouch.core.ExecutionMode
import com.kevinsrm.turbotouch.ui.SwitchRow
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
import com.kevinsrm.turbotouch.service.TurboService

@Composable
fun SettingsScreen(state: TurboUiState, viewModel: TurboViewModel) {
    val context = LocalContext.current
    var connectionPort by remember(state.adbConnectionPort) { mutableStateOf(state.adbConnectionPort.takeIf { it > 0 }?.toString().orEmpty()) }

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
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        SectionCard("Modo de execução") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.executionMode == ExecutionMode.ROOT,
                    onClick = { viewModel.setExecutionMode(ExecutionMode.ROOT) }, label = { Text("Root") })
                FilterChip(selected = state.executionMode == ExecutionMode.STANDARD,
                    onClick = { viewModel.setExecutionMode(ExecutionMode.STANDARD) }, label = { Text("Sem root") })
                FilterChip(selected = state.executionMode == ExecutionMode.ADB_WIFI,
                    onClick = { viewModel.setExecutionMode(ExecutionMode.ADB_WIFI) }, label = { Text("ADB Wi-Fi") })
            }
            Text("ADB Wireless executa comandos como uid shell (não root) no próprio aparelho, por loopback. Ative Depuração sem fio nas opções de desenvolvedor.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = connectionPort,
                onValueChange = { value -> connectionPort = value.filter(Char::isDigit).take(5) },
                label = { Text("Porta de conexão ADB") },
                supportingText = { Text("É diferente da porta temporária usada no pareamento.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val port = connectionPort.toIntOrNull()
                    if (port != null && port in 1..65535) viewModel.setAdbConnectionPort(port)
                    else viewModel.showMessage("Informe a porta de conexão ADB exibida em Depuração sem fio.")
                }) { Text("Salvar porta") }
                OutlinedButton(onClick = { TurboService.requestPairing(context) }) { Text("Parear por notificação") }
                OutlinedButton(onClick = { openIntent(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Opções de desenvolvedor") }
            }
            Text("A notificação permite informar o código de 6 dígitos e a porta temporária. Após parear, informe a porta de conexão mostrada na tela principal de Depuração sem fio.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Monitor flutuante") {
            Text("Requer permissão de sobreposição e monitor ativo.", style = MaterialTheme.typography.bodySmall)
            SwitchRow("FPS", subtitle = "Camada do app via SurfaceFlinger (requer Root ou ADB Wi-Fi)", checked = state.hudFps, onCheckedChange = viewModel::setHudFps)
            SwitchRow("Taxa da tela", checked = state.hudRefresh, onCheckedChange = viewModel::setHudRefresh)
            SwitchRow("Bateria", subtitle = "Nível, temperatura e corrente reportados pelo aparelho", checked = state.hudBattery, onCheckedChange = viewModel::setHudBattery)
            SwitchRow("CPU e GPU", subtitle = "Lidos via shell: /proc/stat e nós sysfs da GPU (requer Root ou ADB Wi-Fi)", checked = state.hudCpuGpu, onCheckedChange = viewModel::setHudCpuGpu)
            OutlinedButton(onClick = { openIntent(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Permitir sobreposição") }
        }

        SectionCard("Permissoes") {
            InfoRow(
                "Root",
                if (state.rootAvailable) "disponivel" else if (state.executionMode == ExecutionMode.ADB_WIFI) "ADB Wi-Fi usa uid shell, não concede root"
                    else "sem root: modo reduzido (refresh global via WRITE_SETTINGS)"
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
                openIntent(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) {
                Text("Notificações")
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
                "Perfil por app e HUD. O FPS depende do acesso ao SurfaceFlinger; uso de GPU pode não estar exposto em alguns fabricantes.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
