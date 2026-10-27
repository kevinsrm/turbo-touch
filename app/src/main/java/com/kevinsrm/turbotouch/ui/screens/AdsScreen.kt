package com.kevinsrm.turbotouch.ui.screens

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.kevinsrm.turbotouch.AppGraph
import com.kevinsrm.turbotouch.ads.AdClickTester
import com.kevinsrm.turbotouch.ads.AdConfig
import com.kevinsrm.turbotouch.ui.SectionCard
import com.kevinsrm.turbotouch.ui.SwitchRow
import com.kevinsrm.turbotouch.ui.TurboUiState
import com.kevinsrm.turbotouch.ui.TurboViewModel

@Composable
fun AdsScreen(state: TurboUiState, viewModel: TurboViewModel, graph: AppGraph) {
    val context = LocalContext.current
    val activity = context as? Activity

    var provider by remember(state.adsConfig) { mutableStateOf(state.adsConfig.provider) }
    var url by remember(state.adsConfig) { mutableStateOf(state.adsConfig.smartLinkUrl) }
    var appId by remember(state.adsConfig) { mutableStateOf(state.adsConfig.admobAppId) }
    var banner by remember(state.adsConfig) { mutableStateOf(state.adsConfig.admobBannerUnit) }
    var interstitial by remember(state.adsConfig) { mutableStateOf(state.adsConfig.admobInterstitialUnit) }
    var interval by remember(state.adsConfig) { mutableStateOf(state.adsConfig.backgroundIntervalSec.toString()) }
    var qaEnabled by remember { mutableStateOf(false) }

    var bannerView by remember { mutableStateOf<FrameLayout?>(null) }

    LaunchedEffect(state.adsConfig) {
        bannerView?.let { graph.ads.showBanner(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Modulo de anuncios") {
            SwitchRow(
                title = "Ativar anuncios",
                subtitle = "vazio = usa o valor do arquivo de configuracao",
                checked = state.adsEnabled,
                onCheckedChange = viewModel::setAdsEnabled
            )
            Text("Provedor", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("smartlink", "admob", "reflective", "none").forEach { option ->
                    FilterChip(
                        selected = provider == option,
                        onClick = { provider = option },
                        label = { Text(option) }
                    )
                }
            }
        }

        SectionCard("Credenciais") {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Smartlink / Direct Link URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = appId,
                onValueChange = { appId = it },
                label = { Text("AdMob app id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = banner,
                onValueChange = { banner = it },
                label = { Text("AdMob banner unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = interstitial,
                onValueChange = { interstitial = it },
                label = { Text("AdMob interstitial unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("Intervalo do slot de fundo (s)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    viewModel.saveAdsConfig(
                        AdConfig(
                            enabled = state.adsEnabled,
                            provider = provider,
                            smartLinkUrl = url,
                            admobAppId = appId,
                            admobBannerUnit = banner,
                            admobInterstitialUnit = interstitial,
                            backgroundIntervalSec = interval.toIntOrNull() ?: 45,
                            debug = state.adsConfig.debug,
                            reflectiveInitClass = state.adsConfig.reflectiveInitClass,
                            reflectiveInitMethod = state.adsConfig.reflectiveInitMethod,
                            reflectiveBannerClass = state.adsConfig.reflectiveBannerClass,
                            reflectiveInterstitialClass = state.adsConfig.reflectiveInterstitialClass
                        )
                    )
                    viewModel.showMessage("configuracao salva no override local")
                }) {
                    Text("Salvar")
                }
                OutlinedButton(onClick = { viewModel.reloadAds() }) {
                    Text("Recarregar")
                }
            }
        }

        SectionCard("Preview") {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> FrameLayout(ctx).also { bannerView = it } }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val shown = activity?.let { graph.ads.showNow(it) } ?: false
                    viewModel.showMessage(if (shown) "anuncio exibido" else "nada pronto para exibir")
                }) {
                    Text("Mostrar agora")
                }
                OutlinedButton(onClick = {
                    val view = bannerView
                    if (view == null) {
                        viewModel.showMessage("banner ainda nao carregado")
                    } else {
                        val delivered = AdClickTester.syntheticTap(view)
                        viewModel.showMessage(
                            if (delivered) "toque sintetico entregue no seu placement"
                            else "toque sintetico nao entregou"
                        )
                    }
                }) {
                    Text("Testar clique (QA)")
                }
            }
            SwitchRow(
                title = "Liberar harness de clique",
                subtitle = "so para QA com os ids de teste do anunciante",
                checked = qaEnabled,
                onCheckedChange = {
                    qaEnabled = it
                    viewModel.setQaClickTest(it)
                }
            )
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard("Origem da configuracao") {
            Text(
                "arquivo: ads_config.properties (raiz do repositorio) -> assets do APK. " +
                    "O que voce salva aqui vira override em filesDir/ads_config_override.properties.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
