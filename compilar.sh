#!/bin/sh
# Compila o NotificadorPDV no Linux e gera NotificadorPDV.jar (requer JDK 8+).
# Executar em segundo plano: nohup java -jar NotificadorPDV.jar config.properties >/dev/null 2>&1 &
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes
javac --release 8 -encoding UTF-8 -cp "lib/*" -d build/classes $(find src -name '*.java') 2>/dev/null || javac -encoding UTF-8 -source 8 -target 8 -cp "lib/*" -d build/classes $(find src -name '*.java')
printf 'Main-Class: notificador.App\nClass-Path: lib/mysql-connector-java-5.1.49.jar\n' > build/manifest.txt
jar cfm NotificadorPDV.jar build/manifest.txt -C build/classes .
echo "OK - NotificadorPDV.jar gerado."
