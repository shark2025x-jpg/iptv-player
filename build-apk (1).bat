@echo off
chcp 65001 >nul
setlocal EnableExtensions
title IPTV Плеер - сборка APK в 1 клик
cd /d "%~dp0"

set "REPO_NAME=iptv-player"
set "PATH=%PATH%;%ProgramFiles%\GitHub CLI;%ProgramFiles%\Git\cmd"

echo ==============================================
echo     IPTV Плеер - сборка APK в 1 клик
echo ==============================================
echo.
echo Что произойдёт:
echo   1. Установятся Git и GitHub CLI (если их нет)
echo   2. Откроется браузер - войдите в свой аккаунт GitHub (один раз)
echo   3. Проект загрузится в ваш репозиторий %REPO_NAME%
echo   4. GitHub соберёт APK (5-8 минут), файл появится в этой папке
echo.
pause

rem ---------- 1. Git и GitHub CLI ----------
where git >nul 2>&1
if errorlevel 1 call :install Git.Git "Git"
set "PATH=%PATH%;%ProgramFiles%\Git\cmd"
where git >nul 2>&1
if errorlevel 1 goto need_tools

where gh >nul 2>&1
if errorlevel 1 call :install GitHub.cli "GitHub CLI"
set "PATH=%PATH%;%ProgramFiles%\GitHub CLI"
where gh >nul 2>&1
if errorlevel 1 goto need_tools

rem ---------- 2. Вход в GitHub ----------
gh auth status >nul 2>&1
if errorlevel 1 (
    echo.
    echo Вход в GitHub: скопируйте код из окна, нажмите Enter и подтвердите в браузере.
    gh auth login --hostname github.com --git-protocol https --web --scopes workflow
) else (
    gh auth status 2>&1 | find "workflow" >nul
    if errorlevel 1 gh auth refresh --hostname github.com --scopes workflow
)
gh auth status >nul 2>&1
if errorlevel 1 (
    echo Не удалось войти в GitHub.
    pause
    exit /b 1
)
gh auth setup-git >nul 2>&1

set "LOGIN="
for /f "delims=" %%i in ('gh api user --jq .login') do set "LOGIN=%%i"
if not defined LOGIN (
    echo Не удалось определить имя пользователя GitHub.
    pause
    exit /b 1
)
set "SLUG=%LOGIN%/%REPO_NAME%"
echo.
echo Аккаунт: %LOGIN%

rem ---------- Файл сборки (всегда актуальный) ----------
if not exist ".github\workflows" mkdir ".github\workflows"
if exist wf.b64 del wf.b64
>>wf.b64 echo bmFtZTogQnVpbGQgQVBLCgpvbjoKICBwdXNoOgogICAgYnJhbmNoZXM6IFsgbWFp
>>wf.b64 echo biwgbWFzdGVyIF0KICB3b3JrZmxvd19kaXNwYXRjaDoKCnBlcm1pc3Npb25zOgog
>>wf.b64 echo IGNvbnRlbnRzOiB3cml0ZQoKam9iczoKICBidWlsZDoKICAgIHJ1bnMtb246IHVi
>>wf.b64 echo dW50dS1sYXRlc3QKICAgIHN0ZXBzOgogICAgICAtIHVzZXM6IGFjdGlvbnMvY2hl
>>wf.b64 echo Y2tvdXRAdjQKICAgICAgLSB1c2VzOiBhY3Rpb25zL3NldHVwLWphdmFAdjQKICAg
>>wf.b64 echo ICAgICB3aXRoOgogICAgICAgICAgZGlzdHJpYnV0aW9uOiB0ZW11cmluCiAgICAg
>>wf.b64 echo ICAgICBqYXZhLXZlcnNpb246IDE3CiAgICAgIC0gbmFtZTogQWNjZXB0IEFuZHJv
>>wf.b64 echo aWQgU0RLIGxpY2Vuc2VzCiAgICAgICAgcnVuOiB5ZXMgfCAiJEFORFJPSURfSE9N
>>wf.b64 echo RS9jbWRsaW5lLXRvb2xzL2xhdGVzdC9iaW4vc2RrbWFuYWdlciIgLS1saWNlbnNl
>>wf.b64 echo cyA+IC9kZXYvbnVsbCB8fCB0cnVlCiAgICAgIC0gdXNlczogZ3JhZGxlL2FjdGlv
>>wf.b64 echo bnMvc2V0dXAtZ3JhZGxlQHY0CiAgICAgICAgd2l0aDoKICAgICAgICAgIGdyYWRs
>>wf.b64 echo ZS12ZXJzaW9uOiA4LjEwLjIKICAgICAgLSBuYW1lOiBCdWlsZCByZWxlYXNlIEFQ
>>wf.b64 echo SwogICAgICAgIHJ1bjogZ3JhZGxlIDphcHA6YXNzZW1ibGVSZWxlYXNlIC0tbm8t
>>wf.b64 echo ZGFlbW9uCiAgICAgIC0gbmFtZTogUmVuYW1lCiAgICAgICAgcnVuOiBjcCBhcHAv
>>wf.b64 echo YnVpbGQvb3V0cHV0cy9hcGsvcmVsZWFzZS9hcHAtcmVsZWFzZS5hcGsgSVBUVi1Q
>>wf.b64 echo bGF5ZXIuYXBrCiAgICAgIC0gdXNlczogYWN0aW9ucy91cGxvYWQtYXJ0aWZhY3RA
>>wf.b64 echo djQKICAgICAgICB3aXRoOgogICAgICAgICAgbmFtZTogSVBUVi1QbGF5ZXItYXBr
>>wf.b64 echo CiAgICAgICAgICBwYXRoOiBJUFRWLVBsYXllci5hcGsKICAgICAgLSBuYW1lOiBQ
>>wf.b64 echo dWJsaXNoIHJlbGVhc2UKICAgICAgICB1c2VzOiBzb2Z0cHJvcHMvYWN0aW9uLWdo
>>wf.b64 echo LXJlbGVhc2VAdjIKICAgICAgICB3aXRoOgogICAgICAgICAgdGFnX25hbWU6IGxh
>>wf.b64 echo dGVzdAogICAgICAgICAgbmFtZTogSVBUViBQbGF5ZXIgKGxhdGVzdCkKICAgICAg
>>wf.b64 echo ICAgIGZpbGVzOiBJUFRWLVBsYXllci5hcGsKICAgICAgICAgIG1ha2VfbGF0ZXN0
>>wf.b64 echo OiB0cnVlCiAgICAgICAgZW52OgogICAgICAgICAgR0lUSFVCX1RPS0VOOiAke3sg
>>wf.b64 echo c2VjcmV0cy5HSVRIVUJfVE9LRU4gfX0K
certutil -decode -f wf.b64 ".github\workflows\build-apk.yml" >nul
del wf.b64 >nul 2>&1

rem ---------- 3. Репозиторий и загрузка ----------
echo [3/5] Загружаю проект в %SLUG% ...
gh repo view %SLUG% >nul 2>&1
if errorlevel 1 gh repo create %REPO_NAME% --public >nul
if not exist ".git" git init -b main >nul 2>&1
git add -A >nul 2>&1
git -c user.name="%LOGIN%" -c user.email="%LOGIN%@users.noreply.github.com" commit -m "IPTV player" >nul 2>&1
git branch -M main >nul 2>&1
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%SLUG%.git
git push -u origin main --force
if errorlevel 1 (
    echo Не удалось загрузить проект на GitHub.
    pause
    exit /b 1
)

rem ---------- 4. Ждём сборку ----------
echo.
echo [4/5] Жду запуск сборки на GitHub...
set "SHA="
for /f %%i in ('git rev-parse HEAD') do set "SHA=%%i"
set "RID="
set /a TRY=0
:findrun
set /a TRY+=1
for /f %%i in ('gh run list -R %SLUG% --commit %SHA% -L 1 --json databaseId --jq ".[0].databaseId" 2^>nul') do set "RID=%%i"
if "%RID%"=="null" set "RID="
if defined RID goto gotrun
if %TRY% GEQ 24 goto norun
timeout /t 5 /nobreak >nul
goto findrun

:norun
echo Сборка не запустилась. Откройте https://github.com/%SLUG%/actions
pause
exit /b 1

:gotrun
echo Сборка идёт (5-8 минут), не закрывайте окно...
gh run watch %RID% -R %SLUG% --interval 10 --exit-status >nul
if errorlevel 1 goto failed

rem ---------- 5. Скачиваем APK ----------
echo.
echo [5/5] Скачиваю APK...
if exist apk-dl rmdir /s /q apk-dl
gh run download %RID% -R %SLUG% -n IPTV-Player-apk -D apk-dl
if not exist "apk-dl\IPTV-Player.apk" goto failed
copy /y "apk-dl\IPTV-Player.apk" "IPTV-Player.apk" >nul
rmdir /s /q apk-dl
echo.
echo ==============================================
echo   ГОТОВО: IPTV-Player.apk лежит в этой папке
echo ==============================================
explorer /select,"%~dp0IPTV-Player.apk"
echo.
echo Установить сейчас?
echo   1 - на этот компьютер (эмулятор BlueStacks)
echo   2 - на телефон по кабелю или на ТВ по Wi-Fi
echo   3 - позже
choice /c 123 /n /m "Ваш выбор: "
if errorlevel 3 exit /b 0
if errorlevel 2 call "%~dp0install.bat" & exit /b 0
call "%~dp0install-pc.bat"
exit /b 0

:failed
echo.
echo [ОШИБКА] Сборка не удалась. Сохраняю журнал в build-error.txt ...
gh run view %RID% -R %SLUG% --log-failed > build-error.txt 2>&1
start notepad build-error.txt
echo Пришлите содержимое build-error.txt - я исправлю проект.
pause
exit /b 1

:need_tools
echo.
echo Не удалось установить Git / GitHub CLI автоматически.
echo Установите вручную: https://git-scm.com/download/win и https://cli.github.com
echo затем запустите этот файл снова.
start "" "https://cli.github.com"
pause
exit /b 1

:install
where winget >nul 2>&1
if errorlevel 1 exit /b 1
echo Устанавливаю %~2 ...
winget install -e --id %~1 --accept-package-agreements --accept-source-agreements
exit /b 0
