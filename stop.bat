@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Stop Rebeautifier Compose services.
REM Usage:
REM   stop.bat
REM   stop.bat --ai
REM   stop.bat --purge
REM   stop.bat --host-gateway

cd /d "%~dp0"

set "PURGE=0"
set "HOST_GATEWAY=0"

:parse
if "%~1"=="" goto parsed
if /I "%~1"=="--ai" shift & goto parse
if /I "%~1"=="/ai" shift & goto parse
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
echo Usage: stop.bat [--purge] [--ai] [--host-gateway]
echo   --purge         Also delete volumes ^(db / covers / ollama data^)
echo   --ai            Accepted for compatibility ^(Ollama always included^)
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

set "DOWN_ARGS="
if "!PURGE!"=="1" (
  set "DOWN_ARGS=-v"
  echo Stopping Rebeautifier and removing volumes...
) else (
  echo Stopping Rebeautifier ^(volumes kept^)...
)

docker compose !FILE_ARGS! down !DOWN_ARGS!
if errorlevel 1 (
  echo Failed to stop Compose services.
  exit /b 1
)

echo Stopped.
exit /b 0
