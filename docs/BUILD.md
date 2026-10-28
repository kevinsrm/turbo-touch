# Build

## Requisitos

| Item | Versao |
| --- | --- |
| JDK | 17 |
| Gradle | 8.14.4 (configurado no wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.4.20 |
| compileSdk / targetSdk | 36 / 35 |
| minSdk | 29 (Android 10) |

## Dependências externas

O cliente ADB Wireless usa `com.flyfishxu:kadb:2.1.1` do Maven Central e o SPAKE2 publicado no JitPack (repositório filtrado no `settings.gradle.kts`). A versão 2.1.1 usa compileSdk 36; as versões 2.1.3 a 2.1.5 usam SDK 37.

O Android Gradle Plugin 8.7.3 pode emitir um aviso por ter sido testado até o SDK 35. Esse aviso é separado da verificação de metadados das dependências e não impede, por si só, o build com SDK 36.

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

## O que o build **nao** faz

* nao assina nada: `assembleRelease` gera APK unsigned. Configure o `signingConfigs`
  do seu lado (ou use o `playApp` do Android Studio).
* nao baixa Android SDK: a maquina precisa da plataforma Android SDK 36 instalada.

## Conflitos de packaging (META-INF)

O `kadb` traz o BouncyCastle 1.83 (`bcpkix`, `bcutil`, `bcprov`) e o `jspecify`
vem junto na resolucao. Esses jars sao *multi-release* e todos carregam os mesmos
arquivos em `META-INF/versions/9`, o que quebrava o build em:

```
Execution failed for task ':app:mergeDebugJavaResource'.
> 4 files found with path 'META-INF/versions/9/OSGI-INF/MANIFEST.MF'
```

O `app/build.gradle.kts` exclui esses metadados no bloco `packaging`:

```kotlin
packaging {
    resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "META-INF/versions/*/OSGI-INF/MANIFEST.MF",
        "META-INF/versions/**/MANIFEST.MF",
        "META-INF/versions/*/module-info.class",
        "META-INF/versions/**/module-info.class",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
        "META-INF/DEPENDENCIES",
        "META-INF/INDEX.LIST",
    )
}
```

Nada disso e usado em runtime no Android: descritores OSGi e `module-info.class`
sao lidos apenas pela JVM/OSGi, nao pela ART. Se um novo jar multi-release entrar
no grafo, o mesmo bloco cobre o conflito.

## Diagnostico

* O servico em primeiro plano nao sobe: Android 15 restringe inicio de FGS em
  alguns momentos — o app inicia o servico pela `MainActivity`, entao abra o app
  uma vez depois da instalacao.
* O painel nao muda de refresh: quase sempre falta root. Sem root o app usa
  `WRITE_SETTINGS` (precisa da concessao em **Ajustes** do app).
* Nenhum comando aparece no historico: o app so executa comandos com root; a tela
  **Ajustes > Historico de comandos** mostra tudo que saiu, com codigo de saida.
