package com.kevinsrm.turbotouch.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SetupWizard(onFinish: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var step by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val granted = remember(step, revision) {
        when (step) {
            0 -> Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            1 -> {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
            }
            2 -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            else -> Settings.canDrawOverlays(context)
        }
    }
    val titles = listOf("Notificações", "Acesso ao uso", "Otimização de bateria", "Sobreposição")
    val descriptions = listOf(
        "Permite exibir o estado do monitor.",
        "Permite detectar o aplicativo em primeiro plano.",
        "Pode ajudar a manter o monitor ativo; aumenta o consumo de bateria.",
        "Necessária para mostrar os widgets flutuantes sobre outros apps."
    )
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Bem-vindo ao TurboTouch — ${step + 1}/4")
        Text(titles[step])
        Text(descriptions[step])
        Text(if (granted) "Concedida" else "Não concedida (opcional)")
        if (error.isNotEmpty()) Text(error)
        Button(onClick = {
            if (step == 0 && Build.VERSION.SDK_INT >= 33) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = when (step) {
                    1 -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    2 -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    3 -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                error = if (runCatching { context.startActivity(intent) }.isSuccess) ""
                    else "Não foi possível abrir as configurações do sistema."
            }
        }, enabled = !granted) { Text("Solicitar permissão") }
        OutlinedButton(onClick = { if (step == 3) onFinish() else { step++; error = "" } }) {
            Text(if (step == 3) "Concluir" else if (granted) "Próximo" else "Pular por enquanto")
        }
    }
}
