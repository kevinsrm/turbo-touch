package com.kevinsrm.turbotouch.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevinsrm.turbotouch.profile.AppProfile
import com.kevinsrm.turbotouch.profile.GameMode
import com.kevinsrm.turbotouch.profile.ProfilePreview
import com.kevinsrm.turbotouch.profile.RefreshTarget
import com.kevinsrm.turbotouch.ui.Divider
import com.kevinsrm.turbotouch.ui.MonoText
import com.kevinsrm.turbotouch.ui.SectionCard
import com.kevinsrm.turbotouch.ui.SwitchRow
import com.kevinsrm.turbotouch.ui.TurboUiState
import com.kevinsrm.turbotouch.ui.TurboViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    packageName: String,
    state: TurboUiState,
    viewModel: TurboViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val existing = state.profiles[packageName]

    var refresh by remember(existing) { mutableStateOf(existing?.refresh ?: RefreshTarget.AUTO) }
    var gameMode by remember(existing) { mutableStateOf(existing?.gameMode ?: GameMode.DEFAULT) }
    var lockClocks by remember(existing) { mutableStateOf(existing?.lockMaxClocks ?: false) }
    var saver by remember(existing) { mutableStateOf(existing?.aggressiveSaver ?: false) }
    var gpu by remember(existing) { mutableStateOf(existing?.gpuBoost ?: false) }
    var scheduler by remember(existing) { mutableStateOf(existing?.schedulerBoost ?: false) }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }

    val label = state.installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    val draft = AppProfile(
        packageName = packageName,
        appLabel = label,
        refresh = refresh,
        gameMode = gameMode,
        lockMaxClocks = lockClocks,
        aggressiveSaver = saver,
        gpuBoost = gpu,
        schedulerBoost = scheduler,
        enabled = enabled
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Refresh alvo") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RefreshTarget.entries.forEach { target ->
                        FilterChip(
                            selected = refresh == target,
                            onClick = { refresh = target },
                            label = { Text(target.label) }
                        )
                    }
                }
            }

            SectionCard("Modo de game / energia") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = gameMode == mode,
                            onClick = { gameMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            SectionCard("Hardware") {
                SwitchRow(
                    title = "Travar clocks maximos",
                    subtitle = "fixed performance mode",
                    checked = lockClocks,
                    onCheckedChange = { lockClocks = it }
                )
                SwitchRow(
                    title = "Economia agressiva",
                    subtitle = "force-idle + RUN_ANY_IN_BACKGROUND ignore",
                    checked = saver,
                    onCheckedChange = { saver = it }
                )
                SwitchRow(
                    title = "GPU no maximo",
                    subtitle = "devfreq min_freq = max_freq",
                    checked = gpu,
                    onCheckedChange = { gpu = it }
                )
                SwitchRow(
                    title = "Boost de scheduler",
                    subtitle = "top-app sched_boost",
                    checked = scheduler,
                    onCheckedChange = { scheduler = it }
                )
                SwitchRow(
                    title = "Perfil ativo",
                    subtitle = "aplicar enquanto o app estiver em primeiro plano",
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }

            SectionCard("Comandos aplicados") {
                val apply = ProfilePreview.commands(draft)
                if (apply.isEmpty()) {
                    Text("nada a executar", style = MaterialTheme.typography.bodyMedium)
                } else {
                    apply.forEach { MonoText(it) }
                }
                Divider()
                Text("Revertidos quando o app sai de primeiro plano", style = MaterialTheme.typography.bodyMedium)
                ProfilePreview.revertCommands(draft).forEach { MonoText(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.saveProfile(draft)
                        onBack()
                        onSaved()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Salvar")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.removeProfile(packageName)
                        onBack()
                    },
                    modifier = Modifier.width(140.dp)
                ) {
                    Text("Remover")
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
