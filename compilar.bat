@echo off
rem Compila o NotificadorPDV e gera NotificadorPDV.jar (requer JDK 8+ no PATH)
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\*.java > build\fontes.txt
rem --release 8 garante compatibilidade com Java 8 num JDK mais novo; o JDK 8 nao tem essa opcao.
javac --release 8 -encoding UTF-8 -cp "lib\*" -d build\classes @build\fontes.txt 2>nul || javac -encoding UTF-8 -source 8 -target 8 -cp "lib\*" -d build\classes @build\fontes.txt || exit /b 1
(
  echo Main-Class: notificador.App
  echo Class-Path: lib/mysql-connector-java-5.1.49.jar
) > build\manifest.txt
jar cfm NotificadorPDV.jar build\manifest.txt -C build\classes . || exit /b 1
echo OK - NotificadorPDV.jar gerado.
