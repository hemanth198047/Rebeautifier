@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Stop Rebeautifier Compose services.
REM Usage:
REM   stop.bat
REM   stop.bat --ai
REM   stop.bat --purge

cd /d "%~dp0"

set "WITH_AI=0"
set "PURGE=0"
set "HOST_GATEWAY=0"

:parse
if "%~1"=="" goto parsed
if /I "%~1"=="--ai" set "WITH_AI=1" & shift & goto parse
if /I "%~1"=="/ai" set "WITH_AI=1" & shift & goto parse
if /I "%~1"=="--purge" set "PURGE=1" & shift & goto parse
if /I "%~1"=="--volumes" set "PURGE=1" & shift & goto parse
if /I "%~1"=="/purge" set "PURGE=1" & shift & goto parse
if /I "%~1"=="--host-gateway" set "HOST_GATEWAY=1" & shift & goto parse
if /I "%~1"=="/host-gateway" set "HOST_GATEWAY=1" & shift & goto parse
if /I "%~1"=="--help" goto help
if /I "%~1"=="-h" goto help
if /I "%~1"=="/?" goto help
echo Unknown option: %~1
goto help

:help
echo Usage: stop.bat [--ai] [--purge] [--host-gateway]
echo   --ai            Include AI profile services when stopping
echo   --purge         Also delete volumes ^(db / covers / ollama data^)
echo   --host-gateway  Match start.bat --host-gateway
exit /b 1

:parsed
where docker >nul 2>&1
if errorlevel 1 (
  echo Docker is required but was not found on PATH.
  exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
  echo Docker Compose v2 "docker compose" is required.
  exit /b 1
)

set "FILE_ARGS=-f docker-compose.yml"
if "!HOST_GATEWAY!"=="1" set "FILE_ARGS=-f docker-compose.yml -f docker-compose.host-gateway.yml"

set "PROFILE_ARGS="
if "!WITH_AI!"=="1" set "PROFILE_ARGS=--profile ai"

set "DOWN_ARGS="
if "!PURGE!"=="1" (
  set "DOWN_ARGS=-v"
  echo Stopping Rebeautifier and removing volumes…
) else (
  echo Stopping Rebeautifier ^(volumes kept^)…
)

docker compose !FILE_ARGS! !PROFILE_ARGS! down !DOWN_ARGS!
if errorlevel 1 (
  echo Failed to stop Compose services.
  exit /b 1
)

echo Stopped.
exit /b 0
