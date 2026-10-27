# Build

## Requisitos

| Item | Versao |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 (o wrapper e regenerado pelo `gradlew` se faltar o jar) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 (Android 10) |

## Passo a passo

```bash
cp local.properties.example local.properties
# sdk.dir=/c/Android/Sdk   (ajuste para a sua maquina)

./gradlew :app:assembleDebug
```

Saida: `app/build/outputs/apk/debug/app-debug.apk`

Instalar no aparelho com root:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## O wrapper

O repositorio nao versiona `gradle/wrapper/gradle-wrapper.jar` (binario). O `gradlew`
deste projeto faz uma de duas coisas:

1. se voce tem `gradle` no PATH, roda `gradle wrapper --gradle-version 8.9` e segue;
2. se nao tem, imprime o que fazer.

Abrindo o projeto no Android Studio, o IDE gera o wrapper sozinho.

## Task de configuracao de anuncios

`app/build.gradle.kts` registra a task `generateAdsConfig`, que le

1. `ads_config.properties` (raiz, versionado) e
2. `ads_config.local.properties` (raiz, ignorado pelo git, tem precedencia)

e escreve `app/build/generated/ads/ads_config.properties`, que entra no APK como asset.
A task roda antes de `mergeAssets` de cada variante.

## O que o build **nao** faz

* nao assina nada: `assembleRelease` gera APK unsigned. Configure o `signingConfigs`
  do seu lado (ou use o `playApp` do Android Studio).
* nao baixa Android SDK: a maquina precisa do SDK 35 instalado.

## Diagnostico

* O servico em primeiro plano nao sobe: Android 15 restringe inicio de FGS em
  alguns momentos — o app inicia o servico pela `MainActivity`, entao abra o app
  uma vez depois da instalacao.
* O painel nao muda de refresh: quase sempre falta root. Sem root o app usa
  `WRITE_SETTINGS` (precisa da concessao em **Ajustes** do app).
* Nenhum comando aparece no historico: o app so executa comandos com root; a tela
  **Ajustes > Historico de comandos** mostra tudo que saiu, com codigo de saida.
