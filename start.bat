@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Start Rebeautifier (Postgres + API + SPA) via Docker Compose.
REM Usage:
REM   start.bat
REM   start.bat --ai
REM   start.bat --ai --pull-model
REM   start.bat --no-rebuild
REM   start.bat --host-gateway

cd /d "%~dp0"

set "WITH_AI=0"
set "PULL_MODEL=0"
set "REBUILD=1"
set "HOST_GATEWAY=0"

:parse
if "%~1"=="" goto parsed
if /I "%~1"=="--ai" set "WITH_AI=1" & shift & goto parse
if /I "%~1"=="/ai" set "WITH_AI=1" & shift & goto parse
if /I "%~1"=="--pull-model" set "PULL_MODEL=1" & shift & goto parse
if /I "%~1"=="--no-rebuild" set "REBUILD=0" & shift & goto parse
if /I "%~1"=="--host-gateway" set "HOST_GATEWAY=1" & shift & goto parse
if /I "%~1"=="/host-gateway" set "HOST_GATEWAY=1" & shift & goto parse
if /I "%~1"=="--help" goto help
if /I "%~1"=="-h" goto help
if /I "%~1"=="/?" goto help
echo Unknown option: %~1
goto help

:help
echo Usage: start.bat [--ai] [--pull-model] [--no-rebuild] [--host-gateway]
echo   --ai            Also start the Ollama AI profile
echo   --pull-model    After start, pull the default Ollama model
echo   --no-rebuild    Skip docker image rebuild
echo   --host-gateway  Nested Docker / broken bridge networking
exit /b 1

:parsed
where docker >nul 2>&1
if errorlevel 1 (
  echo Docker is required but was not found on PATH.
  echo Install Docker Desktop for Windows, start it, then retry.
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop does not appear to be running.
  echo Start Docker Desktop, wait until it says "Running", then retry.
  exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
  echo Docker Compose v2 "docker compose" is required.
  exit /b 1
)

if not exist ".env" if exist ".env.example" (
  copy /Y ".env.example" ".env" >nul
  echo Created .env from .env.example ^(edit passwords for production^).
)

set "API_PORT=8080"
if exist ".env" (
  for /f "usebackq tokens=1,* delims== eol=#" %%A in (".env") do (
    if /I "%%A"=="API_PORT" set "API_PORT=%%B"
  )
)
set "BASE_URL=http://localhost:!API_PORT!"

set "FILE_ARGS=-f docker-compose.yml"
if "!HOST_GATEWAY!"=="1" (
  set "FILE_ARGS=-f docker-compose.yml -f docker-compose.host-gateway.yml"
  echo Using host-gateway Compose override.
)

set "PROFILE_ARGS="
if "!WITH_AI!"=="1" set "PROFILE_ARGS=--profile ai"

set "BUILD_ARGS="
if "!REBUILD!"=="1" set "BUILD_ARGS=--build"

echo Starting Rebeautifier ^(Compose^)...
docker compose !FILE_ARGS! !PROFILE_ARGS! up !BUILD_ARGS! -d
if errorlevel 1 (
  echo Failed to start Compose services.
  exit /b 1
)

echo Waiting for API readiness at !BASE_URL!/api/v1/health/ready ...
set "READY=0"
for /L %%I in (1,1,90) do (
  if "!READY!"=="0" (
    powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri '!BASE_URL!/api/v1/health/ready' -TimeoutSec 3; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
    if not errorlevel 1 (
      set "READY=1"
    ) else (
      timeout /t 2 /nobreak >nul
    )
  )
)

if "!READY!"=="0" (
  echo Timed out waiting for readiness. Recent logs:
  docker compose !FILE_ARGS! !PROFILE_ARGS! logs --tail=80 api db
  if not "!HOST_GATEWAY!"=="1" (
    echo.
    echo If containers cannot reach each other, retry: start.bat --host-gateway
  )
  exit /b 1
)

if "!WITH_AI!"=="1" if "!PULL_MODEL!"=="1" (
  set "MODEL=llama3.2:1b"
  if exist ".env" (
    for /f "usebackq tokens=1,* delims== eol=#" %%A in (".env") do (
      if /I "%%A"=="OLLAMA_MODEL" set "MODEL=%%B"
    )
  )
  echo Pulling Ollama model !MODEL! ^(may take a while^)...
  docker compose !FILE_ARGS! --profile ai exec -T ollama ollama pull !MODEL!
  if errorlevel 1 echo Model pull failed; AI will show offline until the model is available.
)

echo.
echo Rebeautifier is up.
echo   Open:     !BASE_URL!
echo   Demo:     reader / password123
echo   Health:   !BASE_URL!/api/v1/health
if "!WITH_AI!"=="1" (
  echo   Stop:     stop.bat --ai
) else (
  echo   Stop:     stop.bat
  echo   With AI:  start.bat --ai --pull-model
)
echo.
exit /b 0
