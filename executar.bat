@echo off
rem Inicia o notificador em segundo plano (sem janela de consola).
rem Para arrancar com o Windows: crie um atalho deste ficheiro em shell:startup
cd /d "%~dp0"
start "" javaw -jar NotificadorPDV.jar config.properties
