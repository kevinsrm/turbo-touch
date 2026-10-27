@rem
@rem Gradle start-up script for TurboTouch (Windows).
@rem
@rem The binary wrapper jar is not committed to this repository. This script
@rem regenerates it with a locally installed Gradle, or explains how to get one.
@rem
@if "%DEBUG%"=="" @echo off
setlocal

set DIR=%~dp0
set JAR=%DIR%gradle\wrapper\gradle-wrapper.jar

if not exist "%JAR%" (
    where gradle >nul 2>nul
    if "%ERRORLEVEL%"=="0" (
        echo gradle-wrapper.jar ausente - regenerando com o Gradle local...
        gradle -p "%DIR%" wrapper --gradle-version 8.9
    ) else (
        echo ERRO: gradle\wrapper\gradle-wrapper.jar nao existe e nao ha 'gradle' no PATH.
        echo Opcoes: (1) abrir o projeto no Android Studio ^(ele gera o wrapper^),
        echo         (2) instalar o Gradle 8.9 e rodar: gradle wrapper --gradle-version 8.9
        exit /b 1
    )
)

java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
