@echo off
rem Запуск стенда и смоук-проверки на Windows. Логи: папка logs.
chcp 65001 >nul
cd /d "%~dp0"
rem Первичная распаковка, если проект передан архивом
if exist "_bundle.zip" (
  powershell -NoProfile -Command "Expand-Archive -Force -Path '_bundle.zip' -DestinationPath '.'" && del "_bundle.zip"
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\local-check.ps1"
echo.
echo Логи лежат в папке logs. Окно можно закрыть.
pause
