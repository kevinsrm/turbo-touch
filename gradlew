#!/bin/sh
#
# Gradle start-up script for TurboTouch.
#
# The binary wrapper jar is not committed to this repository. This script regenerates
# it with a locally installed Gradle, or explains how to get one.
#
set -e
APP_HOME=$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$JAR" ]; then
    if command -v gradle >/dev/null 2>&1; then
        echo "gradle-wrapper.jar ausente — regenerando com o Gradle local..."
        gradle -p "$APP_HOME" wrapper --gradle-version 8.9
    else
        echo "ERRO: gradle/wrapper/gradle-wrapper.jar nao existe e nao ha 'gradle' no PATH." >&2
        echo "Opcoes: (1) abrir o projeto no Android Studio (ele gera o wrapper)," >&2
        echo "        (2) instalar o Gradle 8.9 e rodar: gradle wrapper --gradle-version 8.9" >&2
        exit 1
    fi
fi

exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
