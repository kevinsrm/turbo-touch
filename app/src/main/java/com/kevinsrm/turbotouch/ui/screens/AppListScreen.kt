package com.kevinsrm.turbotouch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevinsrm.turbotouch.profile.AppProfile
import com.kevinsrm.turbotouch.ui.TurboUiState
import com.kevinsrm.turbotouch.ui.TurboViewModel

@Composable
fun AppListScreen(
    state: TurboUiState,
    viewModel: TurboViewModel,
    onOpenProfile: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val apps = remember(state.installedApps, query) {
        state.installedApps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar app") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val profile = state.profiles[app.packageName]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(app.packageName) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AndroidView(
                            factory = { context -> ImageView(context).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            } },
                            update = { image ->
                                image.setImageDrawable(runCatching {
                                    image.context.packageManager.getApplicationIcon(app.packageName)
                                }.getOrElse { image.context.packageManager.defaultActivityIcon })
                                image.contentDescription = "Icone de ${app.label}"
                            },
                            modifier = Modifier.size(48.dp).padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile != null) {
                                Text(
                                    summarize(profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (profile != null) {
                            AssistChip(
                                onClick = { onOpenProfile(app.packageName) },
                                label = { Text("perfil") }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun summarize(profile: AppProfile): String {
    val parts = mutableListOf<String>()
    if (profile.refresh.hz > 0) parts += "${profile.refresh.hz} Hz"
    if (profile.gameMode != com.kevinsrm.turbotouch.profile.GameMode.DEFAULT) {
        parts += profile.gameMode.label
    }
    if (profile.lockMaxClocks) parts += "clocks"
    if (profile.aggressiveSaver) parts += "economia"
    if (profile.gpuBoost) parts += "gpu"
    if (profile.schedulerBoost) parts += "sched"
    return if (parts.isEmpty()) "perfil vazio" else parts.joinToString(" · ")
}
