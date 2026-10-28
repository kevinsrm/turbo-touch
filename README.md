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

Após salvar um perfil, o app pode apresentar uma oferta patrocinada identificada;
a abertura do smartlink Adsterra no navegador é opcional e limitada a uma oferta
a cada dois minutos enquanto o processo estiver ativo.

---

## Estrutura do projeto

```
turbo-touch/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradlew                       <- regenera o wrapper se o jar faltar
├── docs/
│   ├── BUILD.md
│   └── PER_APP_PROFILES.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/kevinsrm/turbotouch/
        │   ├── TurboApp.kt            (Application + grafo de dependencias)
        │   ├── core/                  (shell, root, historico de comandos)
        │   ├── profile/               (refresh, power, cpu/gpu, watcher, engine)
        │   ├── service/               (TurboService em primeiro plano + boot)
        │   ├── data/                  (preferencias, lista de apps)
        │   ├── ads/                   (oferta patrocinada no navegador)
        │   └── ui/                    (Compose: painel, apps, perfil, ajustes)
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

## Configuração e limitações

No primeiro uso, o assistente explica notificações, acesso ao uso, otimização de
bateria e sobreposição; as permissões podem ser puladas. Em Ajustes escolha Root
ou Sem root (padrão). A troca tenta reverter o perfil e os ajustes globais antes
de passar ao novo modo, mas comandos de reversão podem falhar no dispositivo;
verifique o histórico. O modo sem root nunca chama `su` para comandos privilegiados.

O HUD depende da permissão de sobreposição e do serviço de monitoramento. Ele
mostra Hz do display e nível, temperatura e corrente da bateria quando reportados
pelo aparelho; corrente positiva ou negativa segue a convenção do dispositivo.
FPS de outros apps e uso global de CPU/GPU não são fornecidos por este HUD.
Depuração Wi-Fi/pareamento ADB e notificação interativa ainda não foram
implementados. Nenhum privilégio ADB é simulado ou obtido via Shizuku.
