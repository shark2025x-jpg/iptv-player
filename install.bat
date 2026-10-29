@echo off
chcp 65001 >nul
setlocal EnableExtensions
title IPTV Плеер - установка
cd /d "%~dp0"

rem ===== НАСТРОЙКИ =====
rem Впишите свой логин и имя репозитория на GitHub (откуда скачивать APK), например: ivan/iptv-player
set "REPO=ВАШ_ЛОГИН/iptv-player"
set "APK=IPTV-Player.apk"
rem =====================

set "ADB=adb"
where adb >nul 2>&1
if not errorlevel 1 goto have_adb
if exist "%~dp0platform-tools\adb.exe" (
    set "ADB=%~dp0platform-tools\adb.exe"
    goto have_adb
)

echo [1/3] ADB не найден, скачиваю platform-tools от Google...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile 'pt.zip' -UseBasicParsing; Expand-Archive 'pt.zip' -DestinationPath '.' -Force; Remove-Item 'pt.zip' } catch { exit 1 }"
if not exist "%~dp0platform-tools\adb.exe" (
    echo Не удалось скачать ADB. Проверьте интернет и запустите снова.
    pause
    exit /b 1
)
set "ADB=%~dp0platform-tools\adb.exe"

:have_adb
if exist "%APK%" goto have_apk

echo [2/3] Файл %APK% не найден рядом с батником, скачиваю с GitHub...
echo %REPO% | find "ВАШ_ЛОГИН" >nul
if not errorlevel 1 (
    echo.
    echo Положите %APK% в эту папку ИЛИ откройте install.bat в блокноте
    echo и впишите свой репозиторий в строке REPO.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://github.com/%REPO%/releases/download/latest/%APK%' -OutFile '%APK%' -UseBasicParsing } catch { exit 1 }"
if not exist "%APK%" (
    echo Не удалось скачать APK. Проверьте REPO и что сборка на GitHub завершилась.
    pause
    exit /b 1
)

:have_apk
:menu
cls
echo ==============================================
echo        IPTV Плеер - установка в 1 клик
echo ==============================================
echo.
echo   1 - Телефон по USB-кабелю
echo   2 - Телевизор / приставка / телефон по Wi-Fi
echo   3 - Выход
echo.
set "CH="
set /p CH=Выберите (1-3): 
if "%CH%"=="1" goto usb
if "%CH%"=="2" goto wifi
if "%CH%"=="3" exit /b 0
goto menu

:usb
echo.
echo На телефоне включите: Настройки - О телефоне - 7 раз нажать "Номер сборки",
echo затем Для разработчиков - Отладка по USB. Подключите кабель и разрешите отладку.
echo.
"%ADB%" start-server >nul 2>&1
"%ADB%" devices
echo.
echo Устанавливаю...
"%ADB%" install -r "%APK%"
goto result

:wifi
echo.
echo На ТВ: Настройки - Об устройстве - 7 раз нажать "Сборка", затем
echo Для разработчиков - Отладка по ADB (или по сети) - ВКЛ.
echo IP-адрес смотрите в Настройки - Сеть. ПК и ТВ должны быть в одной сети.
echo.
set "IP="
set /p IP=Введите IP-адрес устройства (например 192.168.1.50): 
if "%IP%"=="" goto menu
"%ADB%" start-server >nul 2>&1
"%ADB%" connect %IP%:5555
echo.
echo Если на экране ТВ появился запрос "Разрешить отладку" - нажмите "Да".
pause
echo Устанавливаю...
"%ADB%" -s %IP%:5555 install -r "%APK%"
goto result

:result
echo.
if errorlevel 1 (
    echo [ОШИБКА] Установка не удалась. Проверьте подключение и разрешение отладки.
) else (
    echo [ГОТОВО] IPTV Плеер установлен. Ищите его в списке приложений.
)
echo.
pause
goto menu
