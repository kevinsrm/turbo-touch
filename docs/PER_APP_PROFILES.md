# Perfis por aplicativo

O motor (`profile/ProfileEngine.kt`) entra no perfil quando o app entra em primeiro
plano e reverte tudo quando ele sai. Nada fica preso no aparelho.

## Matriz de comandos

| Opcao | Aplica | Reverte |
| --- | --- | --- |
| Refresh alvo | `cmd display set-user-preferred-display-mode <w> <h> <hz>`<br>`settings put system min_refresh_rate <hz>`<br>`settings put system peak_refresh_rate <hz>`<br>`settings put secure match_content_frame_rate 0`<br>`cmd display set-match-content-frame-rate-pref never` | `cmd display clear-user-preferred-display-mode`<br>`settings delete system min_refresh_rate`<br>`settings delete system peak_refresh_rate`<br>`settings put secure match_content_frame_rate 1` |
| Modo de game | `cmd game mode <pkg> performance` (ou `battery` / `standard`) | `cmd game mode <pkg> standard` |
| Travar clocks | `cmd power set-fixed-performance-mode-enabled true`<br>`cmd power set-fixed-performance-mode-threshold 95` | `cmd power reset-fixed-performance-mode-enabled` |
| Economia agressiva | `cmd deviceidle force-idle <pkg>`<br>`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` | `cmd deviceidle unforce-idle <pkg>`<br>`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND default` |
| GPU no maximo | `echo <max_freq> > /sys/class/devfreq/<gpu>/min_freq` | `echo <min_freq salvo> > /sys/class/devfreq/<gpu>/min_freq` |
| Boost de scheduler | `echo 1 > /dev/cpuctl/top-app/sched_boost` | `echo 0 > /dev/cpuctl/top-app/sched_boost` |

Globais (tela **Painel**):

| Global | Comando |
| --- | --- |
| Lock de clocks | `cmd power set-fixed-performance-mode-enabled true` |
| Override termico | `cmd thermalservice override-status 0` |
| Refresh global | `settings put system min_refresh_rate` / `peak_refresh_rate` |

## Por que o refresh e global

O Android nao expoe API publica para fixar refresh por aplicativo. O que existe:

* modo de display preferido (`cmd display set-user-preferred-display-mode`) — global;
* politica de content frame rate (`match_content_frame_rate`) — global;
* `Display.setFrameRate()` — so para a propria janela do app.

A estrategia do TurboTouch e a mesma dos apps de "force 120 Hz" do mercado: o painel
fica travado no modo alto enquanto o app monitorado esta em primeiro plano e volta
ao automatico quando ele sai. O modo exato e escolhido a partir de
`DisplayManager.getDisplay(...).supportedModes`, entao o app nunca pede um modo que
o painel nao tem.

## Notas de hardware (leia antes de aplicar em lote)

* **Thermal override nao desliga throttling.** `cmd thermalservice override-status 0`
  trava o *status reportado* pelo framework. O throttling real do SoC continua
  valendo; o que muda e que o Android para de reagir ao calor. Em aparelho
  carregando, use com cuidado.
* **Fixed performance mode** segura os clocks no maximo ate o threshold (95%).
  Bateria e temperatura sobem. O app reverte sozinho quando o app monitorado sai.
* **`fps` do `game_overlay`** (`device_config put game_overlay <pkg> mode=2,fps=120`)
  so aceita divisores da taxa maxima do painel. Valor invalido trava o aparelho —
  por isso o TurboTouch nao escreve essa chave por padrao.
* **`/dev/cpuctl/top-app/sched_boost`** e o caminho do Qualcomm. Em outros SoCs
  (Exynos, Tensor) o caminho e `/dev/stune/boost` ou nao existe — o app procura os
  dois e avisa quando nenhum existe.
* **devfreq da GPU**: em Mali o no costuma ser `gpu`/`gpu0`, em Adreno tambem. O app
  le `max_freq` e `min_freq`, grava o valor minimo anterior para devolver depois.

## Deteccao do app em primeiro plano

| Caminho | Como | Latencia |
| --- | --- | --- |
| Root | `dumpsys activity activities \| grep mResumedActivity` | ~1 s (poll de 900 ms) |
| Sem root | `UsageStatsManager` (evento `ACTIVITY_RESUMED`) | ~1-2 s, exige acesso a dados de uso |

## Permissoes por opcao

| Opcao | Root | Sem root |
| --- | --- | --- |
| Refresh por app | sim | nao (so o global, via `WRITE_SETTINGS`) |
| Modo de game | sim | nao |
| Travar clocks | sim | nao |
| Economia agressiva | sim | nao |
| GPU / scheduler | sim | nao |
