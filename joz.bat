@echo off
setlocal enabledelayedexpansion

:: JOz - AI Coding Agent launcher for Windows
:: Usage: joz run --prompt "Fix the bug"
::        joz run --local --prompt "Hello"
::        joz run                          (interactive REPL)
::        joz init
::        joz config list
::        joz skill list
::        joz plan "Add pagination"

:: Find the script's own directory
set "SCRIPT_DIR=%~dp0"

:: Locate the JAR
set "JAR_PATH=%SCRIPT_DIR%joz-cli\target\joz-cli-0.1.0-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
    echo [ERROR] JOz JAR not found at: %JAR_PATH%
    echo.
    echo Build it first:
    echo   cd %SCRIPT_DIR%
    echo   mvn clean install -DskipTests
    exit /b 1
)

:: Check Java is available
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Install Java 21+ from https://adoptium.net/
    exit /b 1
)

:: Run JOz
java --enable-preview -XX:+UseCompactObjectHeaders ^
     -Dspring.main.banner-mode=off ^
     -Dlogging.level.root=WARN ^
     -Dlogging.level.com.joz=INFO ^
     -jar "%JAR_PATH%" %*
