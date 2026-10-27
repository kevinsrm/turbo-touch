package com.kevinsrm.turbotouch.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevinsrm.turbotouch.ui.Divider
import com.kevinsrm.turbotouch.ui.InfoRow
import com.kevinsrm.turbotouch.ui.MonoText
import com.kevinsrm.turbotouch.ui.SectionCard
import com.kevinsrm.turbotouch.ui.SwitchRow
import com.kevinsrm.turbotouch.ui.TurboUiState
import com.kevinsrm.turbotouch.ui.TurboViewModel

@Composable
fun DashboardScreen(state: TurboUiState, viewModel: TurboViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Aparelho") {
            InfoRow("Root", if (state.rootAvailable) "disponivel" else "sem root (modo reduzido)")
            InfoRow("Monitor", if (state.serviceEnabled) "ativo" else "parado")
            InfoRow(
                "Refresh atual",
                if (state.currentHz > 0f) "${state.currentHz.toInt()} Hz" else "—"
            )
            InfoRow(
                "Modos do painel",
                state.supportedHz.joinToString(" · ") { "${it.toInt()} Hz" }
            )
            InfoRow(
                "Refresh global",
                if (state.globalRefreshHz > 0) "${state.globalRefreshHz} Hz" else "automatico"
            )
        }

        SectionCard("App em primeiro plano") {
            InfoRow("Nome", state.foregroundLabel.ifBlank { "—" })
            InfoRow("Pacote", state.foregroundPackage.ifBlank { "—" })
            Divider()
            val active = state.activeProfile
            if (active != null) {
                Text(
                    "Perfil ativo: ${active.refresh.label} · ${active.gameMode.label}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Nenhum perfil aplicado para este app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        SectionCard("Globais") {
            SwitchRow(
                title = "Lock de clocks maximos",
                subtitle = "cmd power set-fixed-performance-mode-enabled",
                checked = state.lockClocks,
                onCheckedChange = viewModel::setLockClocks
            )
            SwitchRow(
                title = "Override termico",
                subtitle = "cmd thermalservice override-status 0",
                checked = state.thermalOverride,
                onCheckedChange = viewModel::setThermalOverride
            )
            Divider()
            Text("Refresh global do sistema", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Auto", 60 to "60", 90 to "90", 120 to "120", 144 to "144").forEach { (hz, label) ->
                    FilterChip(
                        selected = state.globalRefreshHz == hz,
                        onClick = { viewModel.setGlobalRefresh(hz) },
                        label = { Text(label) }
                    )
                }
            }
        }

        SectionCard("Ultimos comandos") {
            if (state.log.isEmpty()) {
                Text("nenhum comando executado ainda", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.log.take(6).forEach { entry ->
                    MonoText("${entry.time}  ${entry.command}")
                }
            }
        }
    }
}
