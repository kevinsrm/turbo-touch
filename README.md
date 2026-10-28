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

Requisitos: JDK 17, Android Studio Hedgehog+ (ou Gradle 8.9 + Android SDK 35). O cliente ADB usa Kadb (Maven Central) e a dependência SPAKE2 do JitPack.

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
| ADB Wi-Fi | cliente ADB local, emparelhamento Android Wireless Debugging com código, conexão TLS por loopback e comandos com uid `shell` (não root) |
| Monitor | serviço em primeiro plano (`specialUse`), religado no boot; HUD de FPS (best-effort), Hz, bateria e CPU/GPU quando disponíveis |

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
bateria e sobreposição; as permissões podem ser puladas. Em Ajustes escolha Root,
Sem root ou ADB Wi-Fi. Para ADB Wi-Fi, ative Depuração sem fio nas opções de
desenvolvedor e use a ação **Parear por notificação**: envie o código temporário
de seis dígitos e a porta de pareamento. Depois informe a porta de conexão
(separada) mostrada na tela principal de Depuração sem fio. A conexão usa o
cliente ADB TLS nativo em `127.0.0.1`; a identidade ADB fica em armazenamento
privado excluído de backup. A sessão é uid `shell`, não root. O código do
pareamento não é salvo após a tentativa. O modo sem root nunca chama `su`.
A conexão criada com o pareamento é **reaproveitada** entre os comandos do
monitor e só é refeita se a porta mudar, se o transporte cair ou após um minuto
sem comandos. Isso é proposital: um cliente novo por comando fazia o Android
anunciar "Depuração por Wi-Fi conectada" e retirá-la a cada poucos segundos,
enchendo o histórico de notificações.

O HUD requer permissão de sobreposição e o serviço de monitoramento. CPU, GPU e
FPS são lidos **pelo shell** (root ou ADB Wi-Fi, que roda como uid `shell`): desde o
Android 8 o próprio app não lê `/proc/stat` nem os nós sysfs da GPU, então a
leitura direta aparecia sempre como `n/d`. Uma única chamada por amostra coleta
`/proc/stat`, os nós de GPU do fabricante e o `dumpsys SurfaceFlinger --latency`
da camada do app em primeiro plano (com `dumpsys gfxinfo` como reserva). Tudo
continua best-effort: a GPU não é exposta em todos os aparelhos e o FPS fica `n/d`
quando o SurfaceFlinger não lista a camada do app. Sem root/ADB o HUD mostra Hz e
bateria e escreve na última linha o motivo da indisponibilidade.
