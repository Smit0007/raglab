@echo off
call scripts\build.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build step failed. Aborting Docker Compose.
    exit /b %ERRORLEVEL%
)

echo Starting Docker Compose...
docker compose -f infrastructure/docker-compose.yml up --build -d
