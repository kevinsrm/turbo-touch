# TurboTouch

Gerenciador de perfil de hardware **por aplicativo** para Android. Cada app ganha o
combo de refresh, modo de game, teto de clock, economia agressiva e boost de GPU
que voce escolheu — e o app desfaz tudo quando ele sai de primeiro plano.

| Alvo | O que o app faz |
| --- | --- |
| Leitor de PDF / rede social travado em 60 Hz | trava o painel no modo mais alto suportado enquanto o app esta em primeiro plano |
| Jogo pesado | `cmd game mode performance` + fixed performance mode (clock maximo) + GPU presa no `max_freq` + boost do cgroup `top-app` |
| App de mensagens | `cmd deviceidle force-idle` + `RUN_ANY_IN_BACKGROUND ignore` (economia agressiva) enquanto ele esta aberto, restaurado ao sair |
| Monitoramento | servico em primeiro plano com notificacao persistente, religado no boot |

O app tambem traz um **modulo de anuncios** com arquivo de configuracao pronto para
suas credenciais e uma rede que **nao exige o app em loja de aplicativos nem aprovacao**
(detalhes em [docs/ADS.md](docs/ADS.md)).

---

## Estrutura do projeto

```
turbo-touch/
├── ads_config.properties          <- arquivo de credenciais (preencha aqui)
├── ads_config.properties.example  <- copia de referencia
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradlew                       <- regenera o wrapper se o jar faltar
├── docs/
│   ├── BUILD.md
│   ├── ADS.md
│   └── PER_APP_PROFILES.md
└── app/
    ├── build.gradle.kts           <- task que gera o assets/ads_config.properties
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/kevinsrm/turbotouch/
        │   ├── TurboApp.kt            (Application + grafo de dependencias)
        │   ├── core/                  (shell, root, historico de comandos)
        │   ├── profile/               (refresh, power, cpu/gpu, watcher, engine)
        │   ├── service/               (TurboService em primeiro plano + boot)
        │   ├── data/                  (preferencias, lista de apps)
        │   ├── ads/                   (config, provedores, slots, harness de clique)
        │   └── ui/                    (Compose: painel, apps, perfil, ads, ajustes)
        └── res/
```

---

## Build

Requisitos: JDK 17, Android Studio Hedgehog+ (ou Gradle 8.9 + Android SDK 35).

```bash
# 1. apontar o SDK
cp local.properties.example local.properties
# edite sdk.dir=/c/Android/Sdk

# 2. abrir no Android Studio e rodar, ou:
./gradlew :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # release com shrink + ofuscacao
```

O `gradlew` deste repositorio nao versiona o `gradle-wrapper.jar`: ele regenera o
wrapper com o Gradle local ou explica o que fazer. Detalhes em [docs/BUILD.md](docs/BUILD.md).

---

## Permissoes e privilegios

| Recurso | Como funciona |
| --- | --- |
| Root (`su`) | habilita o controle **por app** (game mode, force-idle, devfreq, modo de display) |
| Sem root | refresh global via `WRITE_SETTINGS` + deteccao de primeiro plano via "acesso a dados de uso" |
| Monitor | servico em primeiro plano (tipo `specialUse`), religado no boot |

Sem root o app ainda funciona, mas o refresh vale para o sistema inteiro e a
economia agressiva por app fica indisponivel.

---

## Perfis por app

Cada app tem um perfil com:

* **Refresh alvo** — Auto / 60 / 90 / 120 / 144 Hz
* **Modo de game** — Padrao / Standard / Performance / Battery
* **Travar clocks maximos** — fixed performance mode
* **Economia agressiva** — force-idle + corte de execucao em segundo plano
* **GPU no maximo** — `devfreq min_freq = max_freq`
* **Boost de scheduler** — `top-app/sched_boost = 1`

A tela de edicao mostra **exatamente os comandos** que cada opcao executa e os
comandos de reversao que rodam quando o app sai de primeiro plano.
Matriz completa em [docs/PER_APP_PROFILES.md](docs/PER_APP_PROFILES.md).

---

## Anuncios

Preencha `ads_config.properties` na raiz e reconstrua:

```properties
ads.enabled=true
ads.provider=smartlink
ads.smartlink.url=https://smart.adsterra.com/smartlink/SEU_ZONE_ID
```

O arquivo e lido no build, vira `assets/ads_config.properties` dentro do APK e pode
ser sobrescrito em tempo de execucao pela tela **Ads** do proprio app (as credenciais
ficam em `filesDir/ads_config_override.properties`).

Provedores embutidos:

* `smartlink` — Adsterra / Monetag / PropellerAds "Direct Link". Sem SDK, sem loja de
  aplicativos, sem aprovacao de SDK. Entrega banner + slot de fundo.
* `admob` — Google Mobile Ads (banner + interstitial). Exige o app publicado no Google
  Play para servir em producao.
* `reflective` — liga qualquer outro SDK por nome de classe, sem dependencia de
  compilacao.

O harness **"Testar clique (QA)"** dispara um toque sintetico no seu proprio placement
e confirma se o caminho de clique chegou no SDK. Ele serve para validar a integracao
nos seus ids de teste — nao gera clique em trafego real.

---

## Licenca

Codigo de exemplo. Use como base do seu projeto.
