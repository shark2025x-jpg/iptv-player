@echo off
chcp 65001 >nul
setlocal EnableExtensions
title IPTV Плеер - установка на ПК
cd /d "%~dp0"

rem ===== НАСТРОЙКИ =====
rem Впишите свой логин и репозиторий GitHub, откуда скачать APK (например ivan/iptv-player).
rem Если IPTV-Player.apk лежит рядом с этим файлом - ничего менять не нужно.
set "REPO=ВАШ_ЛОГИН/iptv-player"
set "APK=IPTV-Player.apk"
rem =====================

echo ==============================================
echo     IPTV Плеер - установка на компьютер
echo ==============================================
echo.
echo APK - это приложение Android, на Windows оно работает через эмулятор.
echo Скрипт поставит эмулятор BlueStacks (если его нет) и откроет в нём APK.
echo.

rem --- 1. APK ---
if exist "%APK%" goto have_apk
echo %REPO% | find "ВАШ_ЛОГИН" >nul
if not errorlevel 1 (
    echo Не найден %APK%. Положите его рядом с этим файлом
    echo ИЛИ впишите свой REPO в начале install-pc.bat ^(открыть в блокноте^).
    pause
    exit /b 1
)
echo [1/3] Скачиваю APK с GitHub...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://github.com/%REPO%/releases/download/latest/%APK%' -OutFile '%APK%' -UseBasicParsing } catch { exit 1 }"
if not exist "%APK%" (
    echo Не удалось скачать APK. Проверьте REPO и что сборка на GitHub завершилась.
    pause
    exit /b 1
)
:have_apk

rem --- 2. Эмулятор ---
set "BS="
for %%P in ("%ProgramFiles%\BlueStacks_nxt\HD-Player.exe" "%ProgramFiles%\BlueStacks\HD-Player.exe" "%ProgramFiles(x86)%\BlueStacks_nxt\HD-Player.exe") do (
    if exist %%P set "BS=%%~P"
)
if defined BS goto have_bs

echo [2/3] Эмулятор BlueStacks не найден, устанавливаю...
where winget >nul 2>&1
if errorlevel 1 (
    echo winget не найден. Открываю страницу загрузки BlueStacks - установите его
    echo вручную, затем запустите этот файл снова.
    start "" "https://www.bluestacks.com/"
    pause
    exit /b 1
)
winget install -e --id BlueStack.BlueStacks --accept-package-agreements --accept-source-agreements
for %%P in ("%ProgramFiles%\BlueStacks_nxt\HD-Player.exe" "%ProgramFiles%\BlueStacks\HD-Player.exe") do (
    if exist %%P set "BS=%%~P"
)
if not defined BS (
    echo.
    echo Установка BlueStacks не завершена. Установите его вручную с bluestacks.com
    echo и запустите этот файл снова.
    pause
    exit /b 1
)
:have_bs

rem --- 3. Установка APK в эмулятор ---
echo [3/3] Открываю APK в BlueStacks...
start "" "%APK%"
echo.
echo Если BlueStacks не начал установку сам:
echo   1. Запустите BlueStacks.
echo   2. Перетащите файл %APK% мышкой в окно BlueStacks.
echo После установки значок "IPTV Плеер" появится на главном экране BlueStacks.
echo.
pause
